package com.sualtikasifi.cizimhafiza.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Title + body pair for a local "come back and play" notification. */
data class NotificationText(val title: String, val body: String)

/**
 * Message pools for the daily engagement reminder (see
 * notifications/DailyEngagementWorker.kt). Deliberately picked from
 * `LocalDate.now()` rather than a persisted "last shown index" — that keeps
 * the picker stateless while still avoiding back-to-back repeats: the
 * weekly pool alternates its two variants by ISO week number, and the
 * event-driven pools rotate by day-of-year.
 */
object NotificationMessages {

    // Two variants per weekday so the same day-of-week doesn't say the exact
    // same thing every single week.
    private val weekly: Map<DayOfWeek, List<NotificationText>> = mapOf(
        DayOfWeek.MONDAY to listOf(
            NotificationText("Haftaya güzel başla!", "Birkaç kelime çizmeye ne dersin?"),
            NotificationText("Pazartesi molası", "Pazartesi sendromuna en iyi ilaç: birkaç dakikalık Karalak!")
        ),
        DayOfWeek.TUESDAY to listOf(
            NotificationText("Salı molası", "Kısa bir mola — hadi birkaç kelime çizelim!"),
            NotificationText("Bugün kaç doğru bilirsin?", "Hafızanı test etmenin tam zamanı.")
        ),
        DayOfWeek.WEDNESDAY to listOf(
            NotificationText("Haftanın ortası", "Küçük bir çizim molası iyi gelir."),
            NotificationText("Çarşamba keyfi", "Sıkıldıysan Karalak'ta birkaç tur atmaya ne dersin?")
        ),
        DayOfWeek.THURSDAY to listOf(
            NotificationText("Hafta sonuna doğru", "Skoruna doğru ilerlemeye ne dersin?"),
            NotificationText("Perşembe molası", "Birkaç kelime çiz, hafızanı test et.")
        ),
        DayOfWeek.FRIDAY to listOf(
            NotificationText("Cuma keyfi", "Karalak'la başla — birkaç kelime çiz, günü güzelleştir."),
            NotificationText("Haftayı güzel kapat", "Son bir tur çizim vakti!")
        ),
        DayOfWeek.SATURDAY to listOf(
            NotificationText("Hafta sonu keyfi", "Birkaç kelime çizmeye ne dersin?"),
            NotificationText("Bugün acele yok", "Rahat rahat birkaç tur oyna.")
        ),
        DayOfWeek.SUNDAY to listOf(
            NotificationText("Pazar molası", "Tembelliğe bir de Karalak molası ekle."),
            NotificationText("Haftaya hazırlık", "Isının: birkaç kelime çiz, güzel başla.")
        )
    )

    private val streak = listOf(
        NotificationText("🔥 Serin devam ediyor!", "Bugün de oynayıp bozulmasın."),
        NotificationText("Serini koru", "Bugün oynamazsan serin sıfırlanacak — birkaç dakikan var mı?"),
        NotificationText("Seri seni bekliyor", "Bırakma onu yarı yolda!"),
        NotificationText("Az kaldı", "Bugünkü turunu tamamla, serini koru!"),
        NotificationText("Son bir hatırlatma", "Serini bozmamak için hadi bir tur çiz!"),
        NotificationText("Günlerdir süren serin", "Bugün de sürdür, bırakma!"),
        NotificationText("Bugünü kaçırma", "Serin tehlikede!"),
        NotificationText("Ateşi söndürme", "Bugün de bir tur oyna, serin devam etsin.")
    )

    private val inactivity = listOf(
        NotificationText("Seni özledik!", "Birkaç gündür ortalıkta yoksun, hadi bir tur atalım."),
        NotificationText("Kelimeler seni bekliyor", "Uzun zaman oldu görüşmeyeli."),
        NotificationText("Aramıza dönmeye ne dersin?", "Yeni kelimeler seni bekliyor."),
        NotificationText("Elin çizim özlemiştir", "Hadi geri dön!"),
        NotificationText("Kaçırdıkların birikti", "Birkaç gündür yoksun, telafi etme vakti."),
        NotificationText("Karalak seni bekliyor", "Birkaç dakikan var mı?"),
        NotificationText("Hasret giderelim mi?", "Uzun süredir görüşmedik, bir tur çizelim."),
        NotificationText("Geri dönme vakti!", "Yeni kelimeler, yeni skorlar seni bekliyor.")
    )

    private val rankNudge = listOf(
        NotificationText("Yeni kıdeme çok az kaldı!", "Birkaç oyun daha oyna, {rank} ol!"),
        NotificationText("Sadece {points} puan kaldı", "{rank} kıdemine ulaşmana çok az var!"),
        NotificationText("Yeni kıdemin kapıda", "{rank}. Birkaç kelime daha çiz!"),
        NotificationText("Bugün oynarsan...", "{rank} kıdemine çok yaklaşmış olacaksın!"),
        NotificationText("{rank} kıdemi seni bekliyor", "Son bir hamle yeter!"),
        NotificationText("Az kaldı!", "{points} puan daha ve yeni bir kıdeme adım atıyorsun."),
        NotificationText("Kıdem atlamana ramak kaldı", "Bugün oyna, {rank} ol!"),
        NotificationText("Bir sonraki kıdemin: {rank}", "Hadi son adımı at!")
    )

    fun weeklyVariety(date: LocalDate): NotificationText {
        val options = weekly.getValue(date.dayOfWeek)
        val week = date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
        return options[week % options.size]
    }

    fun streakReminder(date: LocalDate): NotificationText = streak[date.dayOfYear % streak.size]

    fun inactivityReminder(date: LocalDate): NotificationText = inactivity[date.dayOfYear % inactivity.size]

    fun rankNudge(date: LocalDate, rankName: String, pointsRemaining: Int): NotificationText {
        val template = rankNudge[date.dayOfYear % rankNudge.size]
        return NotificationText(
            title = template.title.replace("{rank}", rankName).replace("{points}", pointsRemaining.toString()),
            body = template.body.replace("{rank}", rankName).replace("{points}", pointsRemaining.toString())
        )
    }
}
