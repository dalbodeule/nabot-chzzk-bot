package space.mori.chzzk_bot

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.models.Commands
import space.mori.chzzk_bot.models.Users

object Connector {
    private val dotenv = dotenv()

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = dotenv["DB_URL"]
        driverClassName = "org.mariadb.jdbc.Driver"
        username = dotenv["DB_USER"]
        password = dotenv["DB_PASS"]
        maximumPoolSize = 10
    }
    val dataSource = HikariDataSource(hikariConfig)

    init {
        Database.connect(dataSource)
        val tables = listOf(Users, Commands)

        transaction {
            tables.forEach { table ->
                SchemaUtils.createMissingTablesAndColumns(table)
            }
        }
    }
}