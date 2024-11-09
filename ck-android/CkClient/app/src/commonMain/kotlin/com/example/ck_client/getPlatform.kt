package com.example.ck_client

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
