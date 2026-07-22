package com.findev.fintrack.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: rates gain a decimal place, loans gain contract terms.
 *
 * Rates moved from basis points (0.01%) to thousandths of a percent (0.001%). Real
 * contracts quote three decimals - 28.572% is 2857.2 bp, which an Int cannot hold, and
 * truncating it to 2857 put one contract's final payment 1.86 ₽ out. Stored values are
 * multiplied by ten, which is exact in both directions: no rate already entered can lose
 * anything, because every bp value is a whole number of the new unit.
 *
 * SQLite cannot rename or retype a column in place on old versions, so both tables are
 * rebuilt. Column order and constraints must match what Room generates for the entities,
 * or validation fails on open with an unhelpful diff. Dropping a table also drops its
 * indices, so loan_rate's two are recreated by hand at the end - Room checks those too.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `loan_new` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                `principal_minor` INTEGER NOT NULL, `rate_milli_percent` INTEGER NOT NULL,
                `start_date_epoch_day` INTEGER NOT NULL, `term_months` INTEGER NOT NULL,
                `payment_day` INTEGER NOT NULL, `upfront_fee_minor` INTEGER NOT NULL,
                `monthly_fee_minor` INTEGER NOT NULL, `account_id` TEXT, `category_id` TEXT,
                `reminder_days_before` INTEGER, `fixed_payment_minor` INTEGER,
                `allowed_prepayment_mode` TEXT, `updated_at` INTEGER NOT NULL,
                `is_deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO loan_new (
                id, name, type, principal_minor, rate_milli_percent, start_date_epoch_day,
                term_months, payment_day, upfront_fee_minor, monthly_fee_minor,
                account_id, category_id, reminder_days_before,
                fixed_payment_minor, allowed_prepayment_mode, updated_at, is_deleted
            )
            SELECT
                id, name, type, principal_minor, rate_bp * 10, start_date_epoch_day,
                term_months, payment_day, upfront_fee_minor, monthly_fee_minor,
                account_id, category_id, reminder_days_before,
                NULL, NULL, updated_at, is_deleted
            FROM loan
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE loan")
        db.execSQL("ALTER TABLE loan_new RENAME TO loan")

        db.execSQL(
            """
            CREATE TABLE `loan_rate_new` (
                `id` TEXT NOT NULL, `loan_id` TEXT NOT NULL,
                `rate_milli_percent` INTEGER NOT NULL,
                `effective_from_epoch_day` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO loan_rate_new (
                id, loan_id, rate_milli_percent, effective_from_epoch_day, updated_at, is_deleted
            )
            SELECT id, loan_id, rate_bp * 10, effective_from_epoch_day, updated_at, is_deleted
            FROM loan_rate
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE loan_rate")
        db.execSQL("ALTER TABLE loan_rate_new RENAME TO loan_rate")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_rate_loan_id` ON `loan_rate` (`loan_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_loan_rate_effective_from_epoch_day` " +
                "ON `loan_rate` (`effective_from_epoch_day`)",
        )
    }
}

/**
 * 2 -> 3: transactions learn to be part payments.
 *
 * Until now "оплачено" was decided purely by a settling row existing, so paying less than
 * the instalment closed the whole occurrence. The new column marks money that counts
 * towards an obligation without closing it, and the paid-through queries ignore those rows.
 *
 * Everything already recorded keeps meaning what it meant: default 0, a full payment.
 * ALTER TABLE ADD COLUMN is enough here - nothing is being retyped or renamed, so there is
 * no reason to rebuild the table and risk its indices.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN settles_partial INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * 3 -> 4: a loan can remind more than once.
 *
 * reminder_days_before held a single Int, so a loan could warn a week ahead or the day
 * before but not both - and one warning is either too early to act on or too late to move
 * money. It becomes reminder_days, a comma-separated list.
 *
 * The column changes type, which SQLite cannot do in place, so the table is rebuilt. Every
 * existing value carries over unchanged: a lone number is already a valid one-item list.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `loan_new` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                `principal_minor` INTEGER NOT NULL, `rate_milli_percent` INTEGER NOT NULL,
                `start_date_epoch_day` INTEGER NOT NULL, `term_months` INTEGER NOT NULL,
                `payment_day` INTEGER NOT NULL, `upfront_fee_minor` INTEGER NOT NULL,
                `monthly_fee_minor` INTEGER NOT NULL, `account_id` TEXT, `category_id` TEXT,
                `reminder_days` TEXT, `fixed_payment_minor` INTEGER,
                `allowed_prepayment_mode` TEXT, `updated_at` INTEGER NOT NULL,
                `is_deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO loan_new (
                id, name, type, principal_minor, rate_milli_percent, start_date_epoch_day,
                term_months, payment_day, upfront_fee_minor, monthly_fee_minor,
                account_id, category_id, reminder_days,
                fixed_payment_minor, allowed_prepayment_mode, updated_at, is_deleted
            )
            SELECT
                id, name, type, principal_minor, rate_milli_percent, start_date_epoch_day,
                term_months, payment_day, upfront_fee_minor, monthly_fee_minor,
                account_id, category_id, CAST(reminder_days_before AS TEXT),
                fixed_payment_minor, allowed_prepayment_mode, updated_at, is_deleted
            FROM loan
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE loan")
        db.execSQL("ALTER TABLE loan_new RENAME TO loan")
    }
}

/**
 * 4 -> 5: a loan can say when its first payment falls.
 *
 * Nullable, so every existing loan keeps its current behaviour - the first payment one
 * month after the start date - without the migration having to guess anything.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE loan ADD COLUMN first_payment_epoch_day INTEGER")
    }
}

/**
 * 5 -> 6: accounts gain a manual sort order.
 *
 * Accounts used to sort by name in the picker and the list; the user wants to decide the
 * order. Existing accounts seed their position from rowid so they keep a stable, distinct
 * order (their creation order) rather than all colliding on 0 - reordering renormalises to
 * 0..n-1 from there.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE account SET position = rowid")
    }
}

/**
 * 6 -> 7: categories gain a manual sort order (same idea as accounts).
 *
 * position is per-type in use - the grid and the list query one type at a time - but seeding
 * it from rowid still gives every existing category a distinct, stable start; reordering
 * renormalises a type's categories to 0..n-1 from there.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE category ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE category SET position = rowid")
    }
}

/**
 * 7 -> 8: transactions gain a stable creation time.
 *
 * The feed sorted a day's rows by updated_at, so deleting-and-undoing a transaction (both bump
 * updated_at) shuffled it to the top of its day. created_at is set once and never touched, so
 * the order is stable. Existing rows have no separate creation time, so it is seeded from
 * updated_at - the best estimate available and monotonic with entry order.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE transactions SET created_at = updated_at")
    }
}

/**
 * 8 -> 9: a recurring payment can remind more than once, like a loan.
 *
 * reminder_enabled (a bool) becomes reminder_days, a comma-separated list of lead times.
 * SQLite cannot retype a column in place, so the table is rebuilt. Every payment that had a
 * reminder on carries over as "0" - remind on the day, which is exactly what the flag did -
 * and one that had it off carries over as NULL.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `recurring_payment_new` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL,
                `period` TEXT NOT NULL, `start_date_epoch_day` INTEGER NOT NULL,
                `end_date_epoch_day` INTEGER, `account_id` TEXT NOT NULL,
                `category_id` TEXT NOT NULL, `reminder_days` TEXT,
                `updated_at` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO recurring_payment_new (
                id, name, amount_minor, period, start_date_epoch_day, end_date_epoch_day,
                account_id, category_id, reminder_days, updated_at, is_deleted
            )
            SELECT
                id, name, amount_minor, period, start_date_epoch_day, end_date_epoch_day,
                account_id, category_id,
                CASE WHEN reminder_enabled = 1 THEN '0' ELSE NULL END,
                updated_at, is_deleted
            FROM recurring_payment
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE recurring_payment")
        db.execSQL("ALTER TABLE recurring_payment_new RENAME TO recurring_payment")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_payment_account_id` " +
                "ON `recurring_payment` (`account_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_payment_category_id` " +
                "ON `recurring_payment` (`category_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_payment_start_date_epoch_day` " +
                "ON `recurring_payment` (`start_date_epoch_day`)",
        )
    }
}

/**
 * 9 -> 10: ЖКХ reminders become a payment day plus lead times, for every service.
 *
 * reminder_day (a metered-only "submit readings" day) is replaced by payment_day - the day the
 * bill is due, meaningful for norm and fixed services too - and reminder_days, the same
 * lead-time list a loan has. The table is rebuilt because a column is dropped and the type of
 * the reminder changes. A service that had a reminder day carries it over as its payment day and
 * a "0" lead time (remind on the day, as before); one without keeps a default 1st and no
 * reminder.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `meter_new` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                `billing` TEXT NOT NULL, `tariff_minor` INTEGER NOT NULL,
                `drainage_tariff_minor` INTEGER NOT NULL, `norm_milli` INTEGER NOT NULL,
                `payment_day` INTEGER NOT NULL DEFAULT 1, `reminder_days` TEXT, `group_id` TEXT,
                `updated_at` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO meter_new (
                id, name, type, billing, tariff_minor, drainage_tariff_minor, norm_milli,
                payment_day, reminder_days, group_id, updated_at, is_deleted
            )
            SELECT
                id, name, type, billing, tariff_minor, drainage_tariff_minor, norm_milli,
                CASE WHEN reminder_day BETWEEN 1 AND 31 THEN reminder_day ELSE 1 END,
                CASE WHEN reminder_day BETWEEN 1 AND 31 THEN '0' ELSE NULL END,
                group_id, updated_at, is_deleted
            FROM meter
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE meter")
        db.execSQL("ALTER TABLE meter_new RENAME TO meter")
    }
}
