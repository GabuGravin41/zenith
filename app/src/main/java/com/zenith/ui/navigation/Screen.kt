package com.zenith.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")

    // Finance
    object FinanceDashboard : Screen("finance_dashboard", "Finance")
    object Transactions : Screen("transactions", "Transactions")
    object Analytics : Screen("analytics", "Analytics")

    // Media (Storage Hub)
    object WhatsAppCleaner : Screen("whatsapp_cleaner", "Storage Studio")
    object MediaDetail : Screen("media_detail/{label}", "Media Detail")
    object MediaFilter : Screen("media_filter", "Cleanup Rules")

    // Social
    object SocialStudio : Screen("social_studio", "Social Studio")

    // AI
    object AiChat : Screen("ai_chat", "AI Assistant")

    // Management
    object Settings : Screen("settings", "Settings")
    object Profile : Screen("profile", "Profile")

    // Details
    object TransactionDetail : Screen("transaction_detail/{id}", "Transaction Details")
}
