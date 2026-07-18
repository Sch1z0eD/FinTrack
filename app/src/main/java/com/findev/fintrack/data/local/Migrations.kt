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
