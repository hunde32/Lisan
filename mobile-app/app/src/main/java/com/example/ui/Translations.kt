package com.example.ui

val translations = mapOf(
    "Live" to "ቀጥታ",
    "History" to "ታሪክ",
    "Status" to "ሁኔታ",
    "Settings" to "ቅንብሮች",
    "LISAN" to "ሊሳን",
    "Select glove output language" to "የጓንት ውጤት ቋንቋ ይምረጡ",
    "English" to "እንግሊዝኛ",
    "Amharic" to "አማርኛ",
    "ETHIOGLOVE V2.1" to "ኢትዮ-ጓንት V2.1",
    "Translator" to "አስተርጓሚ",
    "ACTIVE" to "ንቁ",
    "TAP TO LISTEN" to "ለማዳመጥ ይጫኑ",
    "STT LISTENING..." to "በማዳመጥ ላይ...",
    "GLOVE INPUT (AMHARIC)" to "የጓንት ግቤት (አማርኛ)",
    "VOICE OUTPUT (ENGLISH)" to "የድምጽ ውጤት (እንግሊዝኛ)",
    "VOICE OUTPUT (AMHARIC)" to "የድምጽ ውጤት (አማርኛ)",
    "GLOVE INPUT (ENGLISH)" to "የጓንት ግቤት (እንግሊዝኛ)",
    "Today" to "ዛሬ",
    "Yesterday" to "ትላንትና",
    "Battery" to "ባትሪ",
    "Latency" to "ዘግይቶ",
    "Signal" to "ሲግናል",
    "Strong" to "ጠንካራ",
    "None" to "ምንም",
    "Firmware Version" to "የሶፍትዌር ስሪት",
    "v2.1.4 (Up to date)" to "v2.1.4 (የዘመነ)",
    "Dark Theme" to "ጨለማ ገጽታ",
    "Use high-contrast dark mode" to "ከፍተኛ ንፅፅር ጥቁር ገጽታ ይጠቀሙ",
    "UI Language" to "የመተግበሪያ ቋንቋ",
    "Switch app interface to Amharic" to "የመተግበሪያውን ቋንቋ ወደ አማርኛ ይቀይሩ",
    "Translation STT/TTS" to "የትርጉም ቋንቋ",
    "Switch Speech engine to Amharic (am-ET)" to "የንግግር ሞተሩን ወደ አማርኛ ይቀይሩ",
    "Demo Mode" to "የማሳያ ሁነታ",
    "Simulate glove when listening" to "ሲያዳምጡ ጓንትን ያስመስሉ",
    "Connect Glove" to "ጓንት አገናኝ",
    "Disconnect Glove" to "ጓንት አቋርጥ",
    "Connected to HC-05" to "ከ HC-05 ጋር ተገናኝቷል",
    "Tap to connect" to "ለማገናኘት ይጫኑ",
    "No conversations yet." to "እስካሁን ምንም ውይይቶች የሉም።",
    "messages" to "መልዕክቶች"
)

fun String.tr(isAmharic: Boolean): String {
    return if (isAmharic) translations[this] ?: this else this
}
