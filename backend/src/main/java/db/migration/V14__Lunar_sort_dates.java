package db.migration;

import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.model.GenealogyDate;
import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Re-derives every lunar date's sort key and restores the leap flag V9 never backfilled (CLAUDE.md §8.10 #2, #3). */
// Java, not SQL: which month of which year is a tháng nhuận is astronomy, and only LunarCalendar knows it.
public class V14__Lunar_sort_dates extends BaseJavaMigration {

    private static final String LEAP_WORD = "nhuận";

    private static final String SELECT = """
            SELECT id, date_modifier, date_year, date_month, date_day, date_year2, date_month2, date_day2,
                   date_leap_month, date_leap_month2, date_raw
            FROM events
            WHERE date_calendar = 'LUNAR'
            """;

    private static final String UPDATE = "UPDATE events SET date_leap_month = ?, date_sort = ? WHERE id = ?";

    private static final String REVISION = """
            INSERT INTO revisions (entity_type, entity_id, action, before_data, after_data, changed_by, note)
            VALUES ('EVENT', ?, 'UPDATE', '{"leapMonth": false}'::jsonb, '{"leapMonth": true}'::jsonb, NULL, ?)
            """;

    private static final String REVISION_NOTE =
            "V14: ngày nhập ghi \"nhuận\" nhưng chưa đánh dấu tháng nhuận — đánh dấu lại theo chữ đã nhập.";

    /**
     * Recomputes the sort key of every lunar event and sets the leap flag its raw text asks for.
     *
     * @param context the Flyway migration context
     * @throws Exception when the database refuses a statement
     */
    @Override
    public void migrate(Context context) throws Exception {
        // Every lunar sort_date moves: pre-1968 days are now UTC+8 and pre-1582 ones no longer read Julian fields.
        Connection connection = context.getConnection();
        try (Statement select = connection.createStatement();
                ResultSet rows = select.executeQuery(SELECT);
                PreparedStatement update = connection.prepareStatement(UPDATE);
                PreparedStatement revision = connection.prepareStatement(REVISION)) {
            while (rows.next()) {
                long id = rows.getLong("id");
                GenealogyDate date = dateOf(rows);
                boolean restored = !date.isLeapMonth() && asksForLeap(date);
                if (restored) {
                    date.setLeapMonth(true);
                    revision.setLong(1, id);
                    revision.setString(2, REVISION_NOTE);
                    revision.addBatch();
                }
                date.deriveSortDate();
                update.setBoolean(1, date.isLeapMonth());
                LocalDate sort = date.getSortDate();
                if (sort == null) {
                    update.setNull(2, Types.DATE);
                } else {
                    update.setDate(2, Date.valueOf(sort));
                }
                update.setLong(3, id);
                update.addBatch();
            }
            update.executeBatch();
            revision.executeBatch();
        }
    }

    /**
     * Reads one row's date parts into the embeddable the application uses.
     *
     * @param rows the result set, positioned on a row
     * @return the date
     * @throws Exception when a column cannot be read
     */
    private static GenealogyDate dateOf(ResultSet rows) throws Exception {
        GenealogyDate date = new GenealogyDate();
        date.setModifier(DateModifier.valueOf(rows.getString("date_modifier")));
        date.setCalendar(CalendarType.LUNAR);
        date.setYear(rows.getObject("date_year", Integer.class));
        date.setMonth(rows.getObject("date_month", Integer.class));
        date.setDay(rows.getObject("date_day", Integer.class));
        date.setYear2(rows.getObject("date_year2", Integer.class));
        date.setMonth2(rows.getObject("date_month2", Integer.class));
        date.setDay2(rows.getObject("date_day2", Integer.class));
        date.setLeapMonth(rows.getBoolean("date_leap_month"));
        date.setLeapMonth2(rows.getBoolean("date_leap_month2"));
        date.setRaw(rows.getString("date_raw"));
        return date;
    }

    /**
     * Reports whether the family's own text names a leap month that the year really has.
     *
     * @param date the stored date
     * @return true when the flag should be set
     */
    private static boolean asksForLeap(GenealogyDate date) {
        // A range is skipped: its text cannot say which of the two endpoints was the leap month.
        if (date.getModifier() == DateModifier.BETWEEN || date.getYear() == null || date.getMonth() == null
                || date.getRaw() == null) {
            return false;
        }
        String raw = Normalizer.normalize(date.getRaw(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return raw.contains(LEAP_WORD)
                && LunarCalendar.toSolar(new LunarDate(1, date.getMonth(), date.getYear(), true)).isPresent();
    }
}
