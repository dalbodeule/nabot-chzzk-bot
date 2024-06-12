package space.mori.chzzk_bot

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.hibernate.SessionFactory
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import org.hibernate.service.ServiceRegistry
import space.mori.chzzk_bot.discord.User

object Database {
    private val dotenv = dotenv()

    val configuration = Configuration().apply {
        addAnnotatedClass(User::class.java)
        setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
        setProperty("hibernate.show_sql", "true")
        setProperty("hibernate.format_sql", "true")
        setProperty("hibernate.hbm2ddl.auto", "update")
        setProperty("hibernate.hbm2ddl.jdbc", "update")

        setProperty("hibernate.bytecode.use-bytebuddy", "false")

        // HikariCP를 사용하도록 설정
        setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
        setProperty("hibernate.hikari.dataSourceClassName", "org.mariadb.jdbc.MariaDbDataSource")
        setProperty("hibernate.hikari.dataSource.url", dotenv["DB_URL"])
        setProperty("hibernate.hikari.dataSource.user", dotenv["DB_USER"])
        setProperty("hibernate.hikari.dataSource.password", dotenv["DB_PASS"])
        setProperty("hibernate.hikari.maximumPoolSize", "10")
    }

    private val serviceRegistry: ServiceRegistry = StandardServiceRegistryBuilder()
        .applySettings(configuration.properties)
        .build()

    val sessionFactory: SessionFactory = configuration.buildSessionFactory(serviceRegistry)
}