package space.mori.chzzk_bot.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.*

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Connector {
    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = dotenv["DB_URL"]
        driverClassName = "org.mariadb.jdbc.Driver"
        username = dotenv["DB_USER"]
        password = dotenv["DB_PASS"]
        maximumPoolSize = 10
    }
    val dataSource = HikariDataSource(hikariConfig)

    init {
        Database.connect(dataSource)
        val tables = listOf(Users, Commands, Counters, DailyCounters, PersonalCounters, Managers)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(* tables.toTypedArray())
        }
    }
}