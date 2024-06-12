package space.mori.chzzk_bot.discord

import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,

    @Column(length = 255)
    val username: String,

    @Column(length = 64)
    val token: String,

    val discord: Long
)
