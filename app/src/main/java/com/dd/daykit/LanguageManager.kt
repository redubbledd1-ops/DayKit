package com.dd.daykit

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

enum class Language(val code: String, val displayName: String, val locale: Locale) {
    DUTCH("nl", "Nederlands", Locale("nl")),
    ENGLISH("en", "English", Locale.ENGLISH),
    SPANISH("es", "Español", Locale("es")),
    PORTUGUESE("pt", "Português", Locale("pt")),
    GERMAN("de", "Deutsch", Locale.GERMAN),
    FRENCH("fr", "Français", Locale.FRENCH),
    ITALIAN("it", "Italiano", Locale.ITALIAN),
    KOREAN("ko", "한국어", Locale.KOREAN),
    CHINESE("zh", "中文", Locale.CHINESE),
    JAPANESE("ja", "日本語", Locale.JAPANESE),
    RUSSIAN("ru", "Русский", Locale("ru")),
    ARABIC("ar", "العربية", Locale("ar")),
    HINDI("hi", "हिन्दी", Locale("hi")),
    TURKISH("tr", "Türkçe", Locale("tr")),
    POLISH("pl", "Polski", Locale("pl")),
    INDONESIAN("id", "Bahasa Indonesia", Locale("id")),
    UKRAINIAN("uk", "Українська", Locale("uk")),
    VIETNAMESE("vi", "Tiếng Việt", Locale("vi"))
}

object LanguageManager {
    private const val PREFS_NAME = "LanguagePrefs"
    private const val KEY_LANGUAGE = "selected_language"

    var currentLanguage = mutableStateOf(Language.DUTCH)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANGUAGE, Language.DUTCH.code)
        currentLanguage.value = Language.entries.find { it.code == langCode } ?: Language.DUTCH
    }

    fun setLanguage(context: Context, language: Language) {
        currentLanguage.value = language
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }
    
    fun getLocale(): Locale {
        return currentLanguage.value.locale
    }

    // Helper to get map for cleaner code below - Updated for 18 languages
    private fun t(
        nl: String, en: String, es: String, pt: String, de: String, 
        fr: String, it: String, ko: String, zh: String, ja: String,
        ru: String, ar: String, hi: String, tr: String, pl: String,
        id: String, uk: String, vi: String
    ): Map<String, String> {
        return mapOf(
            "nl" to nl, "en" to en, "es" to es, "pt" to pt, "de" to de,
            "fr" to fr, "it" to it, "ko" to ko, "zh" to zh, "ja" to ja,
            "ru" to ru, "ar" to ar, "hi" to hi, "tr" to tr, "pl" to pl,
            "id" to id, "uk" to uk, "vi" to vi
        )
    }

    private val translations = mapOf(
        // Alarm Actions
        "alarm_snooze" to t(
            "Sluimeren", "Snooze", "Posponer", "Soneca", "Schlummern", "Reporter", "Posponi", "다시 알림", "贪睡", "スヌーズ",
            "Отложить", "غفوة", "स्नूज़", "Ertele", "Drzemka", "Tunda", "Відкласти", "Báo lại"
        ),
        "snooze_active" to t(
            "Sluimeren actief", "Snooze active", "Posponer activo", "Soneca ativa", "Schlummern aktiv", "Report actif", "Posponi attivo", "다시 알림 활성", "贪睡中", "スヌーズ中",
            "Отложено", "غفوة نشطة", "स्नूज़ सक्रिय", "Erteleme aktif", "Drzemka aktywna", "Tunda aktif", "Відкладено", "Đang báo lại"
        ),
        "snooze_cancel" to t(
            "Annuleren", "Cancel", "Cancelar", "Cancelar", "Abbrechen", "Annuler", "Annulla", "취소", "取消", "キャンセル",
            "Отмена", "إلغاء", "रद्द करें", "İptal", "Anuluj", "Batal", "Скасувати", "Hủy"
        ),
        "alarm_dismiss" to t(
            "Sluiten", "Dismiss", "Descartar", "Dispensar", "Verwerfen", "Ignorer", "Ignora", "해제", "关闭", "閉じる",
            "Отклонить", "تجاهل", "खारिज", "Kapat", "Odrzuć", "Tutup", "Відхилити", "Bỏ qua"
        ),

        // General
        "save" to t(
            "Opslaan", "Save", "Guardar", "Salvar", "Speichern", "Enregistrer", "Salva", "저장", "保存", "保存",
            "Сохранить", "حفظ", "सहेजें", "Kaydet", "Zapisz", "Simpan", "Зберегти", "Lưu"
        ),
        "cancel" to t(
            "Annuleren", "Cancel", "Cancelar", "Cancelar", "Abbrechen", "Annuler", "Annulla", "취소", "取消", "キャンセル",
            "Отмена", "إلغاء", "रद्द करें", "İptal", "Anuluj", "Batal", "Скасувати", "Hủy"
        ),
        "prompt_save_changes_title" to t(
            "Opslaan?", "Save changes?", "¿Guardar cambios?", "Salvar alterações?", "Änderungen speichern?", "Enregistrer les modifications ?", "Salvare le modifiche?", "변경 사항을 저장할까요?", "要保存更改吗？", "変更を保存しますか？",
            "Сохранить изменения?", "حفظ التغييرات?", "परिवर्तन सहेजें?", "Değişiklikler kaydedilsin mi?", "Zapisać zmiany?", "Simpan perubahan?", "Зберегти зміни?", "Lưu thay đổi?"
        ),
        "dialog_yes" to t(
            "Ja", "Yes", "Sí", "Sim", "Ja", "Oui", "Sì", "예", "是", "はい",
            "Да", "نعم", "हाँ", "Evet", "Tak", "Ya", "Так", "Có"
        ),
        "dialog_no" to t(
            "Nee", "No", "No", "Não", "Nein", "Non", "No", "아니요", "否", "いいえ",
            "Нет", "لا", "नहीं", "Hayır", "Nie", "Tidak", "Ні", "Không"
        ),
        "back" to t(
            "Terug", "Back", "Atrás", "Voltar", "Zurück", "Retour", "Indietro", "뒤로", "返回", "戻る",
            "Назад", "رجوع", "वापस", "Geri", "Wstecz", "Kembali", "Назад", "Quay lại"
        ),
        "delete" to t(
            "Verwijder", "Delete", "Eliminar", "Excluir", "Löschen", "Supprimer", "Elimina", "삭제", "删除", "削除",
            "Удалить", "حذف", "hataen", "Sil", "Usuń", "Hapus", "Видалити", "Xóa"
        ),
        "delete_short" to t(
            "Wis", "Delete", "Borrar", "Excluir", "Löschen", "Effacer", "Canc.", "삭제", "删除", "削除",
            "Удал.", "حذف", "मिटाएं", "Sil", "Usuń", "Hapus", "Видал.", "Xóa"
        ),
        "delete_all" to t(
            "Alles Wissen", "Clear All", "Borrar todo", "Limpar tudo", "Alles löschen", "Tout effacer", "Cancella tutto", "모두 삭제", "全部清除", "全て削除",
            "Очистить всё", "مسح الكل", "सब साफ़ करें", "Tümünü Temizle", "Wyczyść wszystko", "Hapus Semua", "Очистити все", "Xóa tất cả"
        ),
        "rename" to t(
            "Hernoemen", "Rename", "Renombrar", "Renomear", "Umbenennen", "Renommer", "Rinomina", "이름 변경", "重命名", "名前を変更",
            "Переименовать", "إعادة تسمية", "नाम बदलें", "Yeniden Adlandır", "Zmień nazwę", "Ganti Nama", "Перейменувати", "Đổi tên"
        ),
        "name_placeholder" to t(
            "Naam", "Name", "Nombre", "Nome", "Name", "Nom", "Nome", "이름", "名称", "名前",
            "Имя", "الاسم", "नाम", "İsim", "Nazwa", "Nama", "Ім'я", "Tên"
        ),
        "confirm_delete_title" to t(
            "Geschiedenis Wissen", "Clear History", "Borrar historial", "Limpar histórico", "Verlauf löschen", "Effacer l'historique", "Cancella cronologia", "기록 삭제", "清除历史", "履歴を削除",
            "Очистить историю", "مسح السجل", "इतिहास मिटाएं", "Geçmişi Temizle", "Wyczyść historię", "Hapus Riwayat", "Очистити історію", "Xóa lịch sử"
        ),
        "confirm_delete_msg" to t(
            "Weet u zeker dat u alle items wilt verwijderen?", "Are you sure you want to delete all items?", 
            "¿Seguro que desea eliminar todos los elementos?", "Tem certeza que deseja excluir todos os itens?", 
            "Sind Sie sicher, dass Sie alle Elemente löschen möchten?", "Êtes-vous sûr de vouloir tout supprimer ?", 
            "Sei sicuro di voler eliminare tutti gli elementi?", "모든 항목을 삭제하시겠습니까?", 
            "您确定要删除所有项目吗？", "本当に全ての項目を削除しますか？",
            "Вы уверены, что хотите удалить все элементы?", "هل أنت متأكد أنك تريد حذف جميع العناصر؟", "क्या आप वाकई सभी आइटम हटाना चाहते हैं?", 
            "Tüm öğeleri silmek istediğinizden emin misiniz?", "Czy na pewno chcesz usunąć wszystkie elementy?", "Apakah Anda yakin ingin menghapus semua item?", 
            "Ви впевнені, що хочете видалити всі елементи?", "Bạn có chắc chắn muốn xóa tất cả các mục không?"
        ),
        "settings" to t(
            "Instellingen", "Settings", "Ajustes", "Configurações", "Einstellungen", "Paramètres", "Impostazioni", "설정", "设置", "設定",
            "Настройки", "الإعدادات", "सेटिंग्स", "Ayarlar", "Ustawienia", "Pengaturan", "Налаштування", "Cài đặt"
        ),
        "language" to t(
            "Taal", "Language", "Idioma", "Idioma", "Sprache", "Langue", "Lingua", "언어", "语言", "言語",
            "Язык", "اللغة", "भाषा", "Dil", "Język", "Bahasa", "Мова", "Ngôn ngữ"
        ),
        "design" to t(
            "Ontwerp", "Design", "Diseño", "Design", "Design", "Design", "Design", "디자인", "设计", "デザイン",
            "Дизайн", "التصميم", "डिज़ाइन", "Tasarım", "Wygląd", "Desain", "Дизайн", "Thiết kế"
        ),
        "ok" to t(
            "OK", "OK", "OK", "OK", "OK", "OK", "OK", "확인", "确定", "OK",
            "OK", "موافق", "ठीक है", "Tamam", "OK", "OK", "ОК", "OK"
        ),
        "selected" to t(
            "Geselecteerd", "Selected", "Seleccionado", "Selecionado", "Ausgewählt", "Sélectionné", "Selezionato", "선택됨", "已选", "選択中",
            "Выбрано", "المحدد", "चयनित", "Seçildi", "Wybrano", "Dipilih", "Вибрано", "Đã chọn"
        ),
        "previous" to t(
            "Vorige", "Previous", "Anterior", "Anterior", "Vorherige", "Précédent", "Precedente", "이전", "上一步", "前へ",
            "Предыдущий", "السابق", "पिछला", "Önceki", "Poprzedni", "Sebelumnya", "Попередній", "Trước"
        ),
        "next" to t(
            "Volgende", "Next", "Siguiente", "Próximo", "Nächste", "Suivant", "Successivo", "다음", "下一步", "次へ",
            "Следующий", "التالي", "अगला", "Sonraki", "Następny", "Berikutnya", "Наступний", "Tiếp theo"
        ),

        // Navigation
        "nav_components" to t(
            "Onderdelen", "Components", "Componentes", "Componentes", "Komponenten", "Composants", "Componenti", "구성 요소", "组件", "コンポーネント",
            "Компоненты", "المكونات", "अवयव", "Bileşenler", "Komponenty", "Komponen", "Компоненти", "Các thành phần"
        ),
        "nav_stopwatch" to t(
            "Stopwatch", "Stopwatch", "Cronómetro", "Cronômetro", "Stoppuhr", "Chronomètre", "Cronometro", "스톱워치", "秒表", "ストップウォッチ",
            "Секундомер", "ساعة توقيت", "स्टॉपवॉच", "Kronometre", "Stoper", "Stopwatch", "Секундомір", "Đồng hồ bấm giờ"
        ),
        "nav_timer" to t(
            "Timer", "Timer", "Temporizador", "Temporizador", "Timer", "Minuteur", "Timer", "타이머", "计时器", "タイマー",
            "Таймер", "المؤقت", "टाइमर", "Zamanlayıcı", "Minutnik", "Timer", "Таймер", "Hẹn giờ"
        ),
        "nav_appliance" to t(
            "Witgoed", "Appliance", "Electrodom.", "Eletrodom.", "Geräte", "Appareils", "Elettrodom.", "가전", "家电", "家電",
            "Приборы", "أجهزة", "उपकरण", "Cihazlar", "Urządzenia", "Peralatan", "Прилади", "Thiết bị"
        ),
        "nav_calculator" to t(
            "Rekenmachine", "Calculator", "Calculadora", "Calculadora", "Rechner", "Calculatrice", "Calcolatrice", "계산기", "计算器", "電卓",
            "Калькулятор", "آلة حاسبة", "कैलकुलेटर", "Hesap Makinesi", "Kalkulator", "Kalkulator", "Калькулятор", "Máy tính"
        ),
        "navigation_bar_label" to t(
            "Navigatiebalk", "Navigation bar", "Barra de navegación", "Barra de navegação", "Navigationsleiste", "Barre de navigation", "Barra di navigazione", "탐색 막대", "导航栏", "ナビゲーションバー",
            "Панель навигации", "شريط التنقل", "नेविगेशन बार", "Gezinme çubuğu", "Pasek nawigacji", "Bilah navigasi", "Панель навігації", "Thanh điều hướng"
        ),
        "navigation_bar_description" to t(
            "Toon knoppen onderaan het scherm voor snelle navigatie", "Show buttons at the bottom for quick navigation", "Muestra botones en la parte inferior para navegación rápida", "Mostra botões na parte inferior para navegação rápida", "Zeigt unten Schaltflächen für schnelle Navigation", "Affiche des boutons en bas pour une navigation rapide", "Mostra pulsanti in basso per una navigazione rapida", "빠른 탐색을 위해 하단에 버튼 표시", "在底部显示按钮以便快速导航", "素早く移動できるように下部にボタンを表示",
            "Показывать кнопки внизу для быстрой навигации", "إظهار أزرار في الأسفل للتنقل السريع", "तेज़ नेविगेशन के लिए नीचे बटन दिखाएँ", "Hızlı gezinme için altta düğmeleri göster", "Pokaż przyciski na dole dla szybkiej nawigacji", "Tampilkan tombol di bawah untuk navigasi cepat", "Показувати кнопки внизу для швидкої навігації", "Hiển thị nút ở dưới để điều hướng nhanh"
        ),
        "swipe_navigation_label" to t(
            "Swipe navigatie", "Swipe navigation", "Navegación por deslizamiento", "Navegação por deslizar", "Wisch-Navigation", "Navigation par glissement", "Navigazione con swipe", "스와이프 탐색", "滑动导航", "スワイプナビゲーション",
            "Навигация свайпом", "التنقل بالسحب", "स्वाइप नेविगेशन", "Kaydırma ile gezinme", "Nawigacja przesunięciem", "Navigasi geser", "Навігація свайпом", "Dieu huong vuot"
        ),
        "swipe_navigation_description" to t(
            "Schakel swipe acties in of uit, pijltjes blijven werken", "Turn swipe actions on or off, arrows keep working", "Activa o desactiva acciones de deslizamiento, las flechas siguen funcionando", "Ative ou desative ações de deslize, as setas continuam funcionando", "Wischaktionen ein- oder ausschalten, Pfeile bleiben funktionsfähig", "Activez ou désactivez les actions de glissement, les flèches continuent de fonctionner", "Attiva o disattiva le azioni swipe, le frecce continuano a funzionare", "스와이프 동작을 켜거나 끄고, 화살표는 계속 동작합니다", "开启或关闭滑动操作，箭头仍可使用", "スワイプ操作のオン/オフ、矢印は引き続き使えます",
            "Включите или отключите свайпы, стрелки продолжат работать", "تشغيل أو إيقاف إجراءات السحب، والأسهم تظل تعمل", "स्वाइप क्रियाएं चालू या बंद करें, तीर काम करते रहेंगे", "Kaydirma eylemlerini acin veya kapatin, oklar calismaya devam eder", "Wlacz lub wylacz akcje przesuniecia, strzalki nadal dzialaja", "Nyalakan atau matikan aksi geser, panah tetap berfungsi", "Uvimknit abo vymknit dii svaypu, strilky prodovzhat pratsyuvaty", "Bat/tat thao tac vuot, mui ten van hoat dong"
        ),
        "arrow_navigation_label" to t(
            "Pijltjes navigatie", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation",
            "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation", "Arrow navigation"
        ),
        "arrow_navigation_description" to t(
            "Toon of verberg pijltjes op het scherm voor navigatie", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation",
            "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation", "Show or hide on-screen arrows for navigation"
        ),

        // Screen Names
        "screen_agenda_alarm" to t(
            "Agenda Alarm", "Calendar Alarm", "Calendario Alarma", "Calendário Alarme", "Kalender Alarm", "Calendrier Alarme", "Calendario Allarme", "캘린더 알람", "日历闹钟", "カレンダーアラーム",
            "Календарь Будильник", "التقويم المنبه", "कैलेंडर अलार्म", "Takvim Alarm", "Kalendarz Alarm", "Kalender Alarm", "Календар Будильник", "Lịch Báo thức"
        ),
        "alarm_ringing_short" to t(
            "Gaat af", "Ringing", "Sonando", "A tocar", "Klingelt", "Sonnerie", "Sta suonando", "울림", "响铃中", "鳴動中",
            "Звонит", "يرن", "बज रहा", "Çalıyor", "Dzwoni", "Berdering", "Дзвонить", "Đang reo"
        ),
        /** Compacte popup: afteltekst wanneer de eerstvolgende piep op een latere kalenderdag valt. */
        "agenda_popup_countdown_tomorrow" to t(
            "Morgen", "Tomorrow", "Mañana", "Amanhã", "Morgen", "Demain", "Domani", "내일", "明天", "明日",
            "Завтра", "غدًا", "कल", "Yarın", "Jutro", "Besok", "Завтра", "Ngày mai"
        ),
        /** Compacte popup: %d = afgerond naar boven aantal uur. */
        "agenda_popup_countdown_hours" to t(
            "%d uur", "%d hr", "%d h", "%d h", "%d Std", "%d h", "%d h", "%d시간", "%d 小时", "%d時間",
            "%d ч", "%d س", "%d घं", "%d sa", "%d godz", "%d jam", "%d год", "%d g"
        ),
        /** Compacte popup: %d = afgerond naar boven aantal minuten. */
        "agenda_popup_countdown_min" to t(
            "%d min", "%d min", "%d min", "%d min", "%d Min", "%d min", "%d min", "%d분", "%d 分钟", "%d分",
            "%d мин", "%d د", "%d मि", "%d dk", "%d min", "%d mnt", "%d хв", "%d ph"
        ),
        /** Compacte popup onder 10 min: %1$d = minuten, %2$d = seconden (twee cijfers). */
        "agenda_popup_countdown_ms_compact" to t(
            "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds",
            "%1\$d분 %2\$02d초", "%1\$d分%2\$02d秒", "%1\$d分%2\$02d秒",
            "%1\$dм %2\$02dс", "%1\$dm %2\$02ds", "%1\$dm %2\$02ds", "%1\$ddk %2\$02dsn", "%1\$dmin %2\$02ds", "%1\$dm %2\$02ddtk",
            "%1\$dхв %2\$02dс", "%1\$dp %2\$02ds"
        ),
        /** Compacte popup onder 1 minuut: %1$d = seconden. */
        "agenda_popup_countdown_s_compact" to t(
            "%1\$ds", "%1\$ds", "%1\$ds", "%1\$ds", "%1\$ds", "%1\$ds", "%1\$ds",
            "%1\$d초", "%1\$d秒", "%1\$d秒",
            "%1\$dс", "%1\$ds", "%1\$ds", "%1\$dsn", "%1\$ds", "%1\$ddtk",
            "%1\$dс", "%1\$ds"
        ),
        /** %1$s = afspraaktitel, %2$d = minuten tot alarm — compacte tray. */
        "agenda_alarm_notify_upcoming_format" to t(
            "%1\$s over %2\$d min", "%1\$s in %2\$d min", "%1\$s en %2\$d min", "%1\$s em %2\$d min", "%1\$s in %2\$d Min", "%1\$s dans %2\$d min", "%1\$s fra %2\$d min", "%2\$d분 후 %1\$s", "%1\$s %2\$d分钟后", "%2\$d分後の%1\$s",
            "%1\$s через %2\$d мин", "%1\$s خلال %2\$d د", "%2\$d मिन में %1\$s", "%2\$d dk sonra %1\$s", "%1\$s za %2\$d min", "%1\$s dalam %2\$d m", "%1\$s за %2\$d хв", "%1\$s sau %2\$d phút"
        ),
        "agenda_alarm_local_deactivate_confirm_title" to t(
            "Alarm deactiveren?", "Deactivate alarm?", "¿Desactivar alarma?", "Desativar alarme?", "Alarm deaktivieren?", "Désactiver l'alarme ?", "Disattivare sveglia?", "알람 끄기?", "关闭此闹钟？", "このアラームをオフにしますか？",
            "Отключить будильник?", "تعطيل المنبه?", "अलार्म निष्क्रिय करें?", "Alarmı devre dışı bırak?", "Wyłączyć alarm?", "Nonaktifkan alarm?", "Вимкнути будильник?", "Tắt báo thức?"
        ),
        "agenda_alarm_local_activate_confirm_title" to t(
            "Alarm activeren?", "Activate alarm?", "¿Activar alarma?", "Ativar alarme?", "Alarm aktivieren?", "Activer l'alarme ?", "Attivare sveglia?", "알람 켜기?", "启用此闹钟？", "このアラームをオンにしますか？",
            "Включить будильник?", "تفعيل المنبه?", "अलार्म सक्रिय करें?", "Alarmı etkinleştir?", "Włączyć alarm?", "Aktifkan alarm?", "Увімкнути будильник?", "Bật báo thức?"
        ),
        "agenda_alarm_local_confirm_message" to t(
            "Alleen in deze app.\nDe agenda-afspraak wordt niet verwijderd.", "App only.\nThe calendar event is not removed.", "Solo en la app.\nEl evento del calendario no se elimina.", "Só na app.\nO evento da agenda não é removido.", "Nur in der App.\nDer Kalendereintrag wird nicht gelöscht.", "Dans l’app seulement.\nL’événement n’est pas supprimé du calendrier.", "Solo nell’app.\nL’evento di calendario non viene eliminato.", "앱에서만.\n캘린더 일정은 삭제되지 않습니다.", "仅在本应用内。\n日历活动不会被删除。", "アプリ内のみ。\nカレンダーの予定は削除されません。",
            "Только в приложении.\nСобытие в календаре не удаляется.", "في التطبيق فقط.\nموعد التقويم لا يُحذف.", "केवल ऐप में।\nकैलेंडर इवेंट नहीं हटाया जाता।", "Yalnızca uygulamada.\nTakvim etkinliği kaldırılmaz.", "Tylko w aplikacji.\nWydarzenie nie jest usuwane z kalendarza.", "Hanya di app.\nAcara kalender tidak dihapus.", "Лише в застосунку.\nПодія в календарі не видаляється.", "Chỉ trong ứng dụng.\nSự kiện lịch không bị xóa."
        ),
        "screen_stopwatch" to t(
            "Stopwatch", "Stopwatch", "Cronómetro", "Cronômetro", "Stoppuhr", "Chronomètre", "Cronometro", "스톱워치", "秒表", "ストップウォッチ",
            "Секундомер", "ساعة توقيت", "स्टॉपवॉच", "Kronometre", "Stoper", "Stopwatch", "Секундомір", "Đồng hồ bấm giờ"
        ),
        "screen_timer" to t(
            "Timer", "Timer", "Temporizador", "Temporizador", "Timer", "Minuteur", "Timer", "타이머", "计时器", "タイマー",
            "Таймер", "المؤقت", "टाइमर", "Zamanlayıcı", "Minutnik", "Timer", "Таймер", "Hẹn giờ"
        ),
        "screen_appliance_calc" to t(
            "Witgoed", "Appliance", "Electrodom.", "Eletrodom.", "Geräte", "Appareils", "Elettrodom.", "가전", "家电", "家電",
            "Приборы", "أجهزة", "उपकरण", "Cihazlar", "Urządzenia", "Peralatan", "Прилади", "Thiết bị"
        ),
        "screen_calculator" to t(
            "Rekenmachine", "Calculator", "Calculadora", "Calculadora", "Rechner", "Calculatrice", "Calcolatrice", "계산기", "计算器", "電卓",
            "Калькулятор", "آلة حاسبة", "कैलकुलेटर", "Hesap Makinesi", "Kalkulator", "Kalkulator", "Калькулятор", "Máy tính"
        ),
        "screen_weather" to t(
            "Weer", "Weather", "Clima", "Clima", "Wetter", "Météo", "Meteo", "날씨", "天气", "天気",
            "Погода", "الطقس", "मौसम", "Hava Durumu", "Pogoda", "Cuaca", "Погода", "Thời tiết"
        ),
        "screen_settings" to t(
             "Instellingen", "Settings", "Ajustes", "Configurações", "Einstellungen", "Paramètres", "Impostazioni", "설정", "设置", "設定",
            "Настройки", "الإعدادات", "सेटिंग्स", "Ayarlar", "Ustawienia", "Pengaturan", "Налаштування", "Cài đặt"
        ),
        "screen_order_visibility" to t(
             "Volgorde & zichtbaarheid", "Screen Order & Visibility", "Orden y Visibilidad", "Ordem e Visibilidade", "Bildschirmreihenfolge", "Ordre et visibilité", "Ordine e visibilità", "화면 순서 및 가시성", "屏幕顺序和可见性", "画面順序と表示",
            "Порядок экранов", "ترتيب الشاشة والرؤية", "スクリーン क्रम और दृश्यता", "Ekran Sırası ve Görünürlük", "Kolejność i widoczność ekranu", "Urutan & Visibilitas Layar", "Порядок екранів", "Thứ tự & Hiển thị màn hình"
        ),

        // Weather
        "weather_coming_soon" to t(
            "Binnenkort beschikbaar", "Coming soon", "Próximamente", "Em breve", "Demnächst", "Bientôt disponible", "Prossimamente", "곧 출시", "即将推出", "近日公開",
            "Скоро", "قريباً", "जल्द आ रहा है", "Yakında", "Wkrótce", "Segera hadir", "Незабаром", "Sắp ra mắt"
        ),
        "weather_settings_title" to t(
            "Weer instellingen", "Weather settings", "Ajustes del clima", "Configurações do clima", "Wetter-Einstellungen", "Paramètres météo", "Impostazioni meteo", "날씨 설정", "天气设置", "天気設定",
            "Настройки погоды", "إعدادات الطقس", "मौसम सेटिंग्स", "Hava Durumu Ayarları", "Ustawienia pogody", "Pengaturan cuaca", "Налаштування погоди", "Cài đặt thời tiết"
        ),
        "weather_settings_placeholder" to t(
            "Weer alarm instellingen komen hier", "Weather alarm settings will appear here", "Los ajustes de alarma del clima aparecerán aquí", "As configurações de alarme do clima aparecerão aqui", "Wetter-Alarm-Einstellungen erscheinen hier", "Les paramètres d'alarme météo apparaîtront ici", "Le impostazioni degli allarmi meteo appariranno qui", "날씨 알람 설정이 여기에 표시됩니다", "天气闹钟设置将显示在此处", "天気アラーム設定がここに表示されます",
            "Настройки погодных будильников появятся здесь", "ستظهر إعدادات إنذار الطقس هنا", "मौसम अलार्म सेटिंग्स यहाँ दिखाई देंगी", "Hava durumu alarm ayarları burada görünecek", "Ustawienia alarmów pogodowych pojawią się tutaj", "Pengaturan alarm cuaca akan muncul di sini", "Налаштування погодних будильників з'являться тут", "Cài đặt báo thức thời tiết sẽ hiển thị ở đây"
        ),

        // Main Screen
        "main_time" to t(
            "Tijd", "Time", "Hora", "Hora", "Zeit", "Heure", "Ora", "시간", "时间", "時間",
            "Время", "الوقت", "समय", "Zaman", "Czas", "Waktu", "Час", "Thời gian"
        ),
        "main_alarm_in" to t(
            "Alarm over", "Alarm in", "Alarma en", "Alarme em", "Alarm in", "Alarme dans", "Allarme tra", "알람까지", "闹钟倒计时", "アラームまで",
            "Будильник через", "المنبه خلال", "अलार्म में", "Alarm süresi", "Alarm za", "Alarm dalam", "Будильник через", "Báo thức trong"
        ),
        "main_no_alarm" to t(
            "Geen gepland alarm", "No alarm scheduled", "Sin alarma programada", "Sem alarme programado", "Kein Alarm geplant", "Pas d'alarme prévue", "Nessun allarme programmato", "예약된 알람 없음", "无预定闹钟", "予定されたアラームはありません",
            "Нет запланированных будильников", "لا يوجد منبه مجدول", "कोई अलार्म निर्धारित नहीं", "Planlanmış alarm yok", "Brak zaplanowanego alarmu", "Tidak ada alarm terjadwal", "Немає запланованих будильників", "Không có báo thức nào"
        ),
        "main_alarm_now" to t(
            "Alarm gaat nu af!", "Alarm is ringing!", "¡La alarma está sonando!", "O alarme está tocando!", "Alarm klingelt!", "L'alarme sonne !", "L'allarme sta suonando!", "알람이 울립니다!", "闹钟响了!", "アラームが鳴っています！",
            "Будильник звонит!", "المنبه يرن!", "अलार्म बज रहा है!", "Alarm çalıyor!", "Alarm dzwoni!", "Alarm berbunyi!", "Будильник дзвонить!", "Báo thức đang reo!"
        ),
        "notif_alarm_over_colon" to t(
            "Alarm over: %1\$s", "Alarm in: %1\$s", "Alarma en: %1\$s", "Alarme em: %1\$s", "Alarm in: %1\$s", "Alarme dans : %1\$s", "Allarme tra: %1\$s", "알람까지: %1\$s", "闹钟还有: %1\$s", "アラームまで: %1\$s",
            "До будильника: %1\$s", "المنبه بعد: %1\$s", "अलार्म में: %1\$s", "Alarm: %1\$s", "Alarm za: %1\$s", "Alarm dalam: %1\$s", "До будильника: %1\$s", "Báo thức sau: %1\$s"
        ),
        "notif_alarm_at_colon" to t(
            "Alarm om: %1\$s", "Alarm at: %1\$s", "Alarma a las: %1\$s", "Alarme às: %1\$s", "Alarm um: %1\$s", "Alarme à : %1\$s", "Allarme alle: %1\$s", "알람 시각: %1\$s", "响铃时间: %1\$s", "アラーム時刻: %1\$s",
            "Срабатывание в: %1\$s", "التنبيه عند: %1\$s", "अलार्म समय: %1\$s", "Alarm saati: %1\$s", "Alarm o: %1\$s", "Alarm pukul: %1\$s", "Час спрацювання: %1\$s", "Báo thức lúc: %1\$s"
        ),
        "notif_timer_over_colon" to t(
            "Timer over: %1\$s", "Timer in: %1\$s", "Temporizador en: %1\$s", "Temporizador em: %1\$s", "Timer in: %1\$s", "Minuteur dans : %1\$s", "Timer tra: %1\$s", "타이머까지: %1\$s", "计时器还有: %1\$s", "タイマーまで: %1\$s",
            "До таймера: %1\$s", "المؤقت بعد: %1\$s", "टाइमर में: %1\$s", "Zamanlayıcı: %1\$s", "Timer za: %1\$s", "Timer dalam: %1\$s", "До таймера: %1\$s", "Hẹn giờ sau: %1\$s"
        ),
        "notif_timer_ends_at_colon" to t(
            "Timer eindigt om: %1\$s", "Timer ends at: %1\$s", "El temporizador termina a las: %1\$s", "O temporizador termina às: %1\$s", "Timer endet um: %1\$s", "Fin du minuteur à : %1\$s", "Il timer finisce alle: %1\$s", "타이머 종료: %1\$s", "计时器结束: %1\$s", "タイマー終了: %1\$s",
            "Таймер до: %1\$s", "انتهاء المؤقت عند: %1\$s", "टाइमर समाप्ति: %1\$s", "Bitiş: %1\$s", "Koniec timera o: %1\$s", "Timer berakhir pukul: %1\$s", "Таймер до: %1\$s", "Hẹn giờ kết thúc lúc: %1\$s"
        ),
        "notif_bg_timer_title" to t(
            "Timer loopt nog", "Timer still running", "Temporizador activo", "Temporizador em execução", "Timer läuft noch", "Minuteur toujours actif", "Timer ancora attivo", "타이머 진행 중", "计时器仍在运行", "タイマー実行中",
            "Таймер активен", "المؤقت لا يزال يعمل", "टाइमर चालू है", "Zamanlayıcı çalışıyor", "Timer działa", "Timer berjalan", "Таймер активний", "Hẹn giờ vẫn chạy"
        ),
        "notif_bg_timer_active_line" to t(
            "De timer blijft actief op de achtergrond.", "The timer stays active in the background.", "El temporizador sigue activo en segundo plano.", "O temporizador permanece ativo em segundo plano.", "Der Timer läuft weiter im Hintergrund.", "Le minuteur reste actif en arrière-plan.", "Il timer resta attivo in background.", "타이머는 백그라운드에서 계속 실행됩니다.", "计时器在后台保持运行。", "タイマーはバックグラウンドで動作し続けます。",
            "Таймер активен в фоне.", "المؤقت لا يزال نشطًا في الخلفية.", "टाइमर पृष्ठभूमि में सक्रिय रहता है.", "Zamanlayıcı arka planda aktif kalır.", "Timer działa w tle.", "Timer tetap aktif di latar belakang.", "Таймер активний у фоні.", "Hẹn giờ vẫn hoạt động nền."
        ),
        "notif_bg_alarm_title" to t(
            "Alarm actief", "Alarm active", "Alarma activa", "Alarme ativo", "Alarm aktiv", "Alarme active", "Allarme attivo", "알람 활성", "闹钟生效", "アラーム有効",
            "Будильник активен", "التنبيه نشط", "अलार्म सक्रिय", "Alarm aktif", "Alarm aktywny", "Alarm aktif", "Будильник активний", "Báo thức đang bật"
        ),
        "notif_bg_alarm_active_line" to t(
            "Het alarm blijft actief op de achtergrond.", "The alarm stays active in the background.", "La alarma sigue activa en segundo plano.", "O alarme permanece ativo em segundo plano.", "Der Alarm bleibt im Hintergrund aktiv.", "L'alarme reste active en arrière-plan.", "L'allarme resta attivo in background.", "알람은 백그라운드에서 활성 상태입니다.", "闹钟在后台保持生效。", "アラームはバックグラウンドで有効です。",
            "Будильник активен в фоне.", "التنبيه لا يزال نشطًا في الخلفية.", "अलार्म पृष्ठभूमि में सक्रिय रहता है.", "Alarm arka planda aktif kalır.", "Alarm pozostaje aktywny w tle.", "Alarm tetap aktif di latar belakang.", "Будильник активний у фоні.", "Báo thức vẫn hoạt động nền."
        ),
        "notif_bg_last_known" to t(
            "Laatst bekende tijd: %1\$s", "Last known time: %1\$s", "Último tiempo conocido: %1\$s", "Último tempo conhecido: %1\$s", "Zuletzt bekannter Stand: %1\$s", "Dernière valeur connue : %1\$s", "Ultimo tempo noto: %1\$s", "마지막 표시: %1\$s", "上次显示：%1\$s", "直近の表示: %1\$s",
            "Последнее значение: %1\$s", "آخر وقت معروف: %1\$s", "अंतिम ज्ञात समय: %1\$s", "Son bilinen süre: %1\$s", "Ostatni znany czas: %1\$s", "Terakhir diketahui: %1\$s", "Останнє відоме: %1\$s", "Lần cuối hiển thị: %1\$s"
        ),
        "notif_bg_open_live" to t(
            "Open de app voor live aftellen.", "Open the app for a live countdown.", "Abre la app para ver la cuenta atrás en vivo.", "Abra o app para ver a contagem ao vivo.", "App öffnen für Live-Countdown.", "Ouvrez l'app pour un compte à rebours en direct.", "Apri l'app per il conto alla rovescia dal vivo.", "실시간 카운트다운은 앱에서 확인하세요.", "打开应用查看实时倒计时。", "ライブのカウントダウンはアプリで確認してください。",
            "Откройте приложение для живого отсчёта.", "افتح التطبيق للعد التنازلي المباشر.", "लाइव उलटी गिनती के लिए ऐप खोलें.", "Canlı geri sayım için uygulamayı açın.", "Otwórz aplikację, aby zobaczyć odliczanie.", "Buka aplikasi untuk hitungan mundur langsung.", "Відкрийте застосунок для живого відліку.", "Mở ứng dụng để xem đếm ngược trực tiếp."
        ),
        "notif_open_app" to t(
            "Open app", "Open app", "Abrir app", "Abrir app", "App öffnen", "Ouvrir l'app", "Apri app", "앱 열기", "打开应用", "アプリを開く",
            "Открыть", "فتح التطبيق", "ऐप खोलें", "Uygulamayı aç", "Otwórz aplikację", "Buka aplikasi", "Відкрити", "Mở ứng dụng"
        ),
        "notif_bg_sw_title" to t(
            "Stopwatch actief", "Stopwatch active", "Cronómetro activo", "Cronômetro ativo", "Stoppuhr aktiv", "Chronomètre actif", "Cronometro attivo", "스톱워치 활성", "秒表运行中", "ストップウォッチ有効",
            "Секундомер активен", "ساعة التوقيف نشطة", "स्टॉपवॉच सक्रिय", "Kronometre etkin", "Stoper aktywny", "Stopwatch aktif", "Секундомір активний", "Đồng hồ bấm giờ đang bật"
        ),
        "notif_bg_sw_active_line" to t(
            "De stopwatch blijft actief op de achtergrond.", "The stopwatch stays active in the background.", "El cronómetro sigue activo en segundo plano.", "O cronômetro permanece ativo em segundo plano.", "Die Stoppuhr läuft weiter im Hintergrund.", "Le chronomètre reste actif en arrière-plan.", "Il cronometro resta attivo in background.", "스톱워치는 백그라운드에서 계속 실행됩니다.", "秒表在后台保持运行。", "ストップウォッチはバックグラウンドで動作し続けます。",
            "Секундомер активен в фоне.", "ساعة التوقيف لا تزال نشطة في الخلفية.", "स्टॉपवॉच पृष्ठभूमि में सक्रिय रहता है.", "Kronometre arka planda aktif kalır.", "Stoper działa w tle.", "Stopwatch tetap aktif di latar belakang.", "Секундомір активний у фоні.", "Đồng hồ bấm giờ vẫn chạy nền."
        ),
        "notif_bg_open_live_updates" to t(
            "Open de app voor live updates.", "Open the app for live updates.", "Abre la app para ver las actualizaciones en vivo.", "Abra o app para ver atualizações ao vivo.", "App öffnen für Live-Updates.", "Ouvrez l'app pour des mises à jour en direct.", "Apri l'app per aggiornamenti in tempo reale.", "실시간 업데이트는 앱에서 확인하세요.", "打开应用查看实时更新。", "ライブ更新はアプリで確認してください。",
            "Откройте приложение для живых обновлений.", "افتح التطبيق للتحديثات المباشرة.", "लाइव अपडेट के लिए ऐप खोलें.", "Canlı güncellemeler için uygulamayı açın.", "Otwórz aplikację, aby zobaczyć na żywo.", "Buka aplikasi untuk pembaruan langsung.", "Відкрийте застосунок для живих оновлень.", "Mở ứng dụng để xem cập nhật trực tiếp."
        ),
        "days" to t(
            "dagen", "days", "días", "dias", "Tage", "jours", "giorni", "일", "天", "日",
            "дней", "أيام", "दिन", "gün", "dni", "hari", "днів", "ngày"
        ),
        
        // Appliance Calculator (Witgoed)
        "calc_target_label" to t(
            "Hoe laat moet het klaar zijn?", "What time should it be ready?", 
            "¿A qué hora debe estar listo?", "A que horas deve estar pronto?", 
            "Wann soll es fertig sein?", "À quelle heure cela doit-il être prêt ?", 
            "A che ora deve essere pronto?", "언제 완료되어야 합니까?", 
            "什么时候应该准备好？", "何時に終了すべきですか？",
            "Во сколько должно быть готово?", "متى يجب أن يكون جاهزًا؟", "इसे किस समय तैयार होना चाहिए?", 
            "Ne zaman hazır olmalı?", "O której ma być gotowe?", "Kapan harus siap?", 
            "О котрій годині має бути готово?", "Mấy giờ thì xong?"
        ),
        "calc_target_short" to t(
            "Hoe laat klaar?", "What time?", "¿A qué hora?", "Que horas?", "Wann?", "Quelle heure ?", "A che ora?", "몇 시?", "几点？", "何時？",
            "Во сколько?", "متى؟", "किस समय?", "Saat kaç?", "O której?", "Jam berapa?", "О котрій?", "Mấy giờ?"
        ),
        "calc_duration_label" to t(
            "Hoe lang duurt het programma?", "How long is the program?", 
            "¿Cuánto dura el programa?", "Qual a duração do programa?", 
            "Wie lange dauert das Programm?", "Combien de temps dure le programme ?", 
            "Quanto dura il programma?", "프로그램 소요 시간은?", 
            "程序多长时间？", "プログラムの所要時間は？",
            "Сколько длится программа?", "كم مدة البرنامج؟", "प्रोग्राम कितना लंबा है?", 
            "Program ne kadar sürüyor?", "Jak długo trwa program?", "Berapa lama programnya?", 
            "Скільки триває програма?", "Chương trình kéo dài bao lâu?"
        ),
        "calc_duration_short" to t(
            "Hoe lang?", "How long?", "¿Cuánto?", "Quanto?", "Wie lang?", "Combien ?", "Quanto?", "얼마나?", "多久？", "どれくらい？",
            "Сколько?", "كم المدة؟", "कितनी देर?", "Ne kadar?", "Jak długo?", "Berapa lama?", "Скільки?", "Bao lâu?"
        ),
        "hour" to t(
            "Uur", "Hr", "H", "H", "Std", "H", "Ore", "시", "时", "時間",
            "Ч", "س", "घं", "Sa", "Godz", "Jam", "Год", "Giờ"
        ),
        "min" to t(
            "Min", "Min", "Min", "Min", "Min", "Min", "Min", "분", "分", "分",
            "Мин", "د", "मिनट", "Dk", "Min", "Mnt", "Хв", "Phút"
        ),
        "sec" to t(
            "Sec", "Sec", "Seg", "Seg", "Sek", "Sec", "Sec", "초", "秒", "秒",
            "Сек", "ث", "सेकंड", "Sn", "Sek", "Dtk", "Сек", "Giây"
        ),
        "calc_set_delay" to t(
            "Zet startuitstel op:", "Set delay to:", "Retraso:", "Atraso:", "Verzögerung:", "Délai:", "Ritardo:", "지연 설정:", "设置延迟:", "遅延設定:",
            "Задержка:", "تعيين التأخير:", "देरी सेट करें:", "Gecikme ayarla:", "Ustaw opóźnienie:", "Atur penundaan:", "Затримка:", "Đặt độ trễ:"
        ),
        "calc_delay_short" to t(
            "Startuitstel:", "Delay:", "Retraso:", "Atraso:", "Verzögerung:", "Délai:", "Ritardo:", "지연:", "延迟:", "遅延:",
            "Задержка:", "التأخير:", "देरी:", "Gecikme:", "Opóźnienie:", "Penundaan:", "Затримка:", "Độ trễ:"
        ),
        "calc_start_at" to t(
            "Start om:", "Start at:", "Inicio:", "Início:", "Start um:", "Début à:", "Inizio:", "시작:", "开始于:", "開始時間:",
            "Начало в:", "البدء в:", "शुरू:", "Başlangıç:", "Start o:", "Mulai pukul:", "Початок о:", "Bắt đầu lúc:"
        ),
        "calc_save_button" to t(
            "Sla op", "Save", "Guardar", "Salvar", "Speichern", "Enregistrer", "Salva", "저장", "保存", "保存",
            "Сохранить", "حفظ", "सहेजें", "Kaydet", "Zapisz", "Simpan", "Зберегти", "Lưu"
        ), 
        "calc_saved_history" to t(
            "Opgeslagen", "History", "Historial", "Histórico", "Verlauf", "Historique", "Cronologia", "기록", "历史", "履歴",
            "История", "السجل", "इतिहास", "Geçmiş", "Historia", "Riwayat", "Історія", "Lịch sử"
        ),
        "time_witgoed" to t(
            "Time Witgoed", "Time Appliance", "Temporizar", "Temporizar", "Timer starten", "Minuterie", "Timer", "타이머 시작", "定时器", "タイマー開始",
            "Запустить таймер", "بدء المؤقت", "टाइमर शुरू", "Zamanlayıcı", "Uruchom timer", "Mulai Timer", "Запустити таймер", "Bắt đầu hẹn giờ"
        ),
        // Standaardnaam voor een timer gestart vanuit Witgoed zonder gekozen opgeslagen naam —
        // gebruikt door GlobalTimerManager.startTimer() zodat de timer overal (Timer-pagina,
        // in-app popups, systeemmeldingen buiten de app) als "Witgoed" te herkennen is i.p.v. naamloos.
        "appliance_default_timer_name" to t(
            "Witgoed", "Appliance", "Electrodoméstico", "Eletrodoméstico", "Haushaltsgerät", "Électroménager", "Elettrodomestico", "가전제품", "家电", "家電",
            "Бытовая техника", "الأجهزة المنزلية", "उपकरण", "Beyaz eşya", "AGD", "Peralatan", "Побутова техніка", "Thiết bị gia dụng"
        ),
        "calc_ready_at" to t(
            "Klaar om:", "Ready at:", "Listo a:", "Pronto às:", "Fertig um:", "Prêt à:", "Pronto alle:", "완료:", "完成于:", "終了:",
            "Готово в:", "جاهز في:", "तैयार:", "Hazır:", "Gotowe o:", "Siap pukul:", "Готово о:", "Xong lúc:"
        ),
        "calc_duration" to t(
            "Duur:", "Duration:", "Duración:", "Duração:", "Dauer:", "Durée:", "Durata:", "기간:", "时长:", "所要時間:",
            "Длит.:", "المدة:", "अवधि:", "Süre:", "Czas:", "Durasi:", "Трив.:", "Thời lượng:"
        ),
        "calc_rename_title" to t(
            "Hernoem", "Rename", "Renombrar", "Renomear", "Umbenennen", "Renommer", "Rinomina", "이름 변경", "重命名", "名前変更",
            "Переим.", "إعادة تسمية", "नाम बदलें", "Yeniden Adlandır", "Zmień nazwę", "Ganti Nama", "Перейм.", "Đổi tên"
        ),
        "calc_confirm_delete" to t(
            "Weet u zeker dat u alle opgeslagen berekeningen wilt verwijderen?", "Are you sure you want to delete all saved calculations?", 
            "¿Seguro que desea eliminar todos los cálculos?", "Tem certeza que deseja excluir todos os cálculos?", 
            "Sind Sie sicher, dass Sie alle Berechnungen löschen möchten?", "Voulez-vous supprimer tous les calculs ?", 
            "Sicuro di voler eliminare tutti i calcoli?", "모든 저장된 계산을 삭제하시겠습니까?", 
            "确定要删除所有保存的计算吗？", "保存された計算を全て削除しますか？",
            "Вы уверены, что хотите удалить все расчеты?", "هل أنت متأكد أنك تريد حذف جميع الحسابات؟", "क्या आप वाकई सभी सहेजी गई गणनाओं को हटाना चाहते हैं?",
            "Tüm kayıtlı hesaplamaları silmek istediğinizden emin misiniz?", "Czy na pewno chcesz usunąć wszystkie obliczenia?", "Yakin ingin menghapus semua perhitungan?",
            "Ви впевнені, що хочете видалити всі розрахунки?", "Bạn có chắc muốn xóa tất cả các tính toán không?"
        ),

        // Calculator
        "calculator_title" to t(
            "Rekenmachine", "Calculator", "Calculadora", "Calculadora", "Rechner", "Calculatrice", "Calcolatrice", "계산기", "计算器", "電卓",
            "Калькулятор", "آلة حاسبة", "कैलकुलेटर", "Hesap Makinesi", "Kalkulator", "Kalkulator", "Калькулятор", "Máy tính"
        ),
        "calc_expression" to t(
            "Som", "Expression", "Expresión", "Expressão", "Ausdruck", "Expression", "Espressione", "수식", "表达式", "式",
            "Выражение", "تعبير", "अभिव्यक्ति", "İfade", "Wyrażenie", "Ekspresi", "Вираз", "Biểu thức"
        ),
        "calc_result" to t(
            "Resultaat", "Result", "Resultado", "Resultado", "Ergebnis", "Résultat", "Risultato", "결과", "结果", "結果",
            "Результат", "النتيجة", "परिणाम", "Sonuç", "Wynik", "Hasil", "Результат", "Kết quả"
        ),
        "calc_clear_all" to t(
            "Alles Wissen", "Clear All", "Borrar todo", "Limpar tudo", "Alles löschen", "Tout effacer", "Cancella tutto", "모두 삭제", "全部清除", "全て削除",
            "Очистить всё", "مسح الكل", "सब साफ़ करें", "Tümünü Temizle", "Wyczyść wszystko", "Hapus Semua", "Очистити все", "Xóa tất cả"
        ),

        // Stopwatch
        "sw_start" to t(
            "Start", "Start", "Inicio", "Iniciar", "Start", "Démarrer", "Avvia", "시작", "开始", "スタート",
            "Старт", "بدء", "शुरू", "Başlat", "Start", "Mulai", "Старт", "Bắt đầu"
        ),
        "sw_lap" to t(
            "Ronde", "Lap", "Vuelta", "Volta", "Runde", "Tour", "Giro", "랩", "圈", "ラップ",
            "Круг", "دورة", "लैप", "Tur", "Okrążenie", "Putaran", "Коло", "Vòng"
        ),
        "sw_pause" to t(
            "Pauze", "Pause", "Pausa", "Pausa", "Pause", "Pause", "Pausa", "일시정지", "暂停", "一時停止",
            "Пауза", "إيقاف مؤقت", "विराम", "Duraklat", "Pauza", "Jeda", "Пауза", "Tạm dừng"
        ),
        "sw_resume" to t(
            "Hervat", "Resume", "Reanudar", "Retomar", "Fortsetzen", "Reprendre", "Riprendi", "재개", "恢复", "再開",
            "Продолжить", "استئناف", "फिर शुरू", "Devam Et", "Wznów", "Lanjut", "Продовжити", "Tiếp tục"
        ),
        "sw_stop" to t(
            "Stop", "Stop", "Stop", "Parar", "Stopp", "Arrêter", "Stop", "정지", "停止", "ストップ",
            "Стоп", "إيقاف", "रुको", "Durdur", "Stop", "Berhenti", "Стоп", "Dừng"
        ),
        "sw_popup" to t(
            "Toon melding", "Show notification", "Mostrar notificación", "Mostrar notificação", "Benachrichtigung anzeigen", "Afficher notification", "Mostra notifica", "알림 표시", "显示通知", "通知を表示",
            "Показать уведомление", "إظهار الإشعار", "सूचना दिखाएं", "Bildirimi Göster", "Pokaż powiadomienie", "Tampilkan notifikasi", "Показати сповіщення", "Hiển thị thông báo"
        ),
        "sw_saved_title" to t(
            "Opgeslagen Tijden", "Saved Times", "Tiempos guardados", "Tempos salvos", "Gesp. Zeiten", "Temps enreg.", "Tempi salvati", "저장된 시간", "已存时间", "保存された時間",
            "Сохр. время", "الأوقات المحفوظة", "सहेजा गया समय", "Kaydedilen Zamanlar", "Zapisane czasy", "Waktu Disimpan", "Збережений час", "Thời gian đã lưu"
        ),
        "sw_confirm_delete" to t(
            "Weet u zeker dat u alle opgeslagen tijden wilt verwijderen?", "Are you sure you want to delete all saved times?",
            "¿Seguro que desea eliminar todos los tiempos?", "Tem certeza que deseja excluir todos os tempos?",
            "Alle Zeiten löschen?", "Supprimer tous les temps ?", "Eliminare tutti i tempi?",
            "모든 시간을 삭제하시겠습니까?", "删除所有时间？", "全ての時間を削除しますか？",
            "Удалить все времена?", "حذف كل الأوقات؟", "सभी समय हटाएं?", "Tüm zamanları sil?", "Usunąć wszystkie czasy?", "Hapus semua waktu?", "Видалити весь час?", "Xóa tất cả thời gian?"
        ),
        "sw_empty" to t(
            "Geen opgeslagen tijden.", "No saved times.", "Sin tiempos guardados.", "Sem tempos salvos.", "Keine gespeicherten Zeiten.", "Aucun temps enregistré.", "Nessun tempo salvato.", "저장된 시간 없음.", "无保存时间。", "保存された時間はありません。",
            "Нет сохр. времени.", "لا توجد أوقات", "कोई समय नहीं", "Kayıtlı zaman yok.", "Brak czasów.", "Tidak ada waktu.", "Немає часу.", "Không có thời gian."
        ),
        "sw_rename_title" to t(
            "Hernoem Sessie", "Rename Session", "Renombrar Sesión", "Renomear Sessão", "Sitzung umbenennen", "Renommer la session", "Rinomina sessione", "세션 이름 변경", "重命名会话", "セッション名を変更",
            "Переим. сессию", "إعادة تسمية الجلسة", "सत्र का नाम बदलें", "Oturumu Yeniden Adlandır", "Zmień nazwę sesji", "Ganti Nama Sesi", "Перейм. сесію", "Đổi tên phiên"
        ),

        // Timer
        "timer_set_title" to t(
            "Stel tijd in", "Set timer", "Configurar temporizador", "Definir temporizador", "Timer einstellen", "Régler minuterie", "Imposta timer", "타이머 설정", "设置计时器", "タイマー設定",
            "Уст. таймер", "ضبط المؤقت", "टाइमर सेट करें", "Zamanlayıcı Ayarla", "Ustaw minutnik", "Atur timer", "Вст. таймер", "Đặt hẹn giờ"
        ),
        "timer_reset_input" to t(
            "Tijd resetten", "Reset time", "Restablecer tiempo", "Repor tempo", "Zeit zurücksetzen", "Réinitialiser", "Reimposta tempo", "시간 초기화", "重置时间", "時間をリセット",
            "Сброс времени", "إعادة ضبط الوقت", "समय रीसेट", "Sıfırla", "Resetuj czas", "Atur ulang waktu", "Скинути час", "Đặt lại thời gian"
        ),
        "timer_finished_title" to t(
            "Timer Afgelopen!", "Timer Finished!", "¡Tiempo terminado!", "Tempo esgotado!", "Zeit abgelaufen!", "Terminé !", "Tempo scaduto!", "타이머 종료!", "计时结束!", "タイマー終了!",
            "Таймер истек!", "انتهى المؤقت!", "टाइमर समाप्त!", "Süre Doldu!", "Koniec czasu!", "Waktu Habis!", "Час вийшов!", "Hết giờ!"
        ),
        "timer_finished_msg" to t(
            "De ingestelde tijd is verstreken.", "The set time has passed.", "El tiempo establecido ha pasado.", "O tempo definido passou.", "Die eingestellte Zeit ist abgelaufen.", "Le temps imparti est écoulé.", "Il tempo impostato è scaduto.", "설정된 시간이 지났습니다.", "设定时间已过。", "設定時間が経過しました。",
            "Установленное время истекло.", "لقد انقضى الوقت المحدد.", "निर्धारित समय बीत चुका है।", "Ayarlanan süre geçti.", "Ustawiony czas minął.", "Waktu yang ditentukan telah berlalu.", "Встановлений час минув.", "Thời gian đã đặt đã trôi qua."
        ),
        "timer_stop_alarm" to t(
            "Stop Alarm", "Stop Alarm", "Parar Alarma", "Parar Alarme", "Alarm stoppen", "Arrêter l'alarme", "Ferma allarme", "알람 정지", "停止闹钟", "アラーム停止",
            "Стоп будильник", "إيقاف التنبيه", "अलार्म रोकें", "Alarmı Durdur", "Zatrzymaj alarm", "Hentikan Alarm", "Стоп будильник", "Dừng báo thức"
        ),
        "timer_ready_popup_title" to t(
            "Timer klaar", "Timer finished", "Temporizador listo", "Temporizador pronto", "Timer fertig", "Minuteur terminé", "Timer completato", "타이머 완료", "计时结束", "タイマー完了",
            "Таймер готов", "انتهى المؤقت", "टाइमर पूरा", "Süre doldu", "Koniec czasu", "Timer selesai", "Таймер готовий", "Hết giờ"
        ),
        /** Compacte tray / lockscreen: kort; onderdeel staat in ondertitel ([screen_timer]). */
        "timer_finished_tray_title" to t(
            "Klaar", "Done", "Listo", "Pronto", "Fertig", "Fin", "Fatto", "완료", "完成", "完了",
            "Готово", "تم", "हो गया", "Bitti", "Gotowe", "Selesai", "Готово", "Xong"
        ),
        "timer_alarm_restart" to t(
            "Opnieuw", "Restart", "Reiniciar", "Reiniciar", "Neu starten", "Relancer", "Riavvia", "다시 시작", "重新开始", "再開",
            "Заново", "إعادة", "फिर शुरू", "Yeniden", "Od nowa", "Ulang", "Знову", "Chạy lại"
        ),
        "timer_alarm_close" to t(
            "Stop", "Stop", "Parar", "Parar", "Stopp", "Arrêter", "Stop", "중지", "停止", "停止",
            "Стоп", "إيقاف", "रोकें", "Durdur", "Stop", "Stop", "Стоп", "Dừng"
        ),
        "timer_saved_title" to t(
            "Opgeslagen Timers", "Saved Timers", "Temporizadores guardados", "Temporizadores salvos", "Gespeicherte Timer", "Minuteries enregistrées", "Timer salvati", "저장된 타이머", "已存计时器", "保存されたタイマー",
            "Сохр. таймеры", "المؤقتات المحفوظة", "सहेजे गए टाइमर", "Kayıtlı Zamanlayıcılar", "Zapisane minutniki", "Timer Disimpan", "Збережені таймери", "Hẹn giờ đã lưu"
        ),
        "timer_settings_title" to t(
            "Timer Instellingen", "Timer Settings", "Ajustes Temporizador", "Config. Temporizador", "Timer-Einstellungen", "Paramètres minuterie", "Impostazioni Timer", "타이머 설정", "计时器设置", "タイマー設定",
            "Настр. таймера", "إعدادات المؤقت", "टाइマー सेटिंग्स", "Zamanlayıcı Ayarları", "Ustawienia minutnika", "Pengaturan Timer", "Налашт. таймера", "Cài đặt hẹn giờ"
        ),
        "timer_volume" to t(
            "Alarm Volume", "Alarm Volume", "Volumen de Alarma", "Volume do Alarme", "Alarm-Lautstärke", "Volume d'alarme", "Volume allarme", "알람 볼륨", "闹钟音量", "アラーム音量",
            "Громкость", "حجم التنبيه", "अलार्म वॉल्यूम", "Alarm Sesi", "Głośność alarmu", "Volume Alarm", "Гучність", "Âm lượng báo thức"
        ),
        "timer_vibrate" to t(
            "Trillen", "Vibrate", "Vibrar", "Vibrar", "Vibrieren", "Vibrer", "Vibrazione", "진동", "震动", "バイブレーション",
            "Вибрация", "اهتزاز", "कंपन", "Titreşim", "Wibracja", "Getar", "Вібрація", "Rung"
        ),
        "timer_use_mobile_volume" to t(
            "Mobiel geluid volume gebruiken", "Use mobile sound volume", "Usar volumen del móvil", "Usar volume do celular", "Handy-Lautstärke verwenden", "Utiliser volume mobile", "Usa volume telefono", "모바일 볼륨 사용", "使用手机音量", "携帯音量を使用",
            "Исп. громк. телефона", "استخدام صوت الهاتف", "मोबाइल वॉल्यूम उपयोग करें", "Mobil ses seviyesi kullan", "Użyj głośności telefonu", "Gunakan volume ponsel", "Вик. гучність телефону", "Dùng âm lượng điện thoại"
        ),
        "timer_select_sound" to t(
            "Selecteer Alarmgeluid", "Select Alarm Sound", "Seleccionar sonido", "Selecionar som", "Alarmton wählen", "Choisir sonnerie", "Scegli suono", "알람 소리 선택", "选择闹钟声音", "アラーム音を選択",
            "Выбрать звук", "اختر صوت التنبيه", "अलार्म ध्वनि चुनें", "Alarm Sesi Seç", "Wybierz dźwięk", "Pilih Suara Alarm", "Вибрати звук", "Chọn âm báo"
        ),
        "timer_popup" to t(
            "Toon melding", "Show notification", "Mostrar notificación", "Mostrar notificação", "Benachrichtigung anzeigen", "Afficher notification", "Mostra notifica", "알림 표시", "显示通知", "通知を表示",
            "Показать уведомление", "إظهار الإشعار", "सूचना दिखाएं", "Bildirimi Göster", "Pokaż powiadomienie", "Tampilkan notifikasi", "Показати сповіщення", "Hiển thị thông báo"
        ),
        "timer_multi_timer" to t(
            "Meerdere timers tegelijk", "Multiple timers simultaneously", "Múltiples temporizadores", "Múltiplos temporizadores", "Mehrere Timer gleichzeitig", "Minuteries simultanées", "Timer multipli", "동시 다중 타이머", "同时多个计时器", "同時に複数タイマー",
            "Несколько таймеров", "مؤقتات متعددة", "एकाधिक टाइमर", "Birden fazla zamanlayıcı", "Wiele minutników", "Beberapa timer sekaligus", "Кілька таймерів", "Nhiều hẹn giờ cùng lúc"
        ),
        "timer_confirm_delete" to t(
            "Weet u zeker dat u alle opgeslagen timers wilt verwijderen?", "Are you sure you want to delete all saved timers?",
            "¿Seguro que desea eliminar todos los temporizadores?", "Tem certeza que deseja excluir todos os temporizadores?",
            "Alle Timer löschen?", "Supprimer toutes les minuteries ?", "Eliminare tutti i timer?",
            "모든 타이머를 삭제하시겠습니까?", "删除所有计时器？", "全てのタイマーを削除しますか？",
            "Удалить все таймеры?", "حذف كل المؤقتات؟", "सभी टाइमर हटाएं?", "Tüm zamanlayıcıları sil?", "Usunąć wszystkie minutniki?", "Hapus semua timer?", "Видалити всі таймери?", "Xóa tất cả hẹn giờ?"
        ),
        "timer_rename_title" to t(
            "Hernoem Timer", "Rename Timer", "Renombrar Temporizador", "Renomear Temporizador", "Timer umbenennen", "Renommer minuterie", "Rinomina Timer", "타이머 이름 변경", "重命名计时器", "タイマー名を変更",
            "Переим. таймер", "إعادة تسمية المؤقت", "टाइマー का नाम बदलें", "Zamanlayıcıyı Yeniden Adlandır", "Zmień nazwę minutnika", "Ganti Nama Timer", "Перейм. таймер", "Đổi tên hẹn giờ"
        ),

        // Upcoming Alarms
        "upcoming_title" to t(
            "Aankomende Alarms", "Upcoming Alarms", "Próximas Alarmas", "Próximos Alarmes", "Kommende Alarme", "Alarmes à venir", "Prossimi allarmi", "다가오는 알람", "即将到来的闹钟", "今後のアラーム",
            "Будущие буд.", "المنبهات القادمة", "आगामी अलार्म", "Yaklaşan Alarmlar", "Nadchodzące alarmy", "Alarm Mendatang", "Майбутні буд.", "Báo thức sắp tới"
        ),
        "upcoming_none" to t(
            "Geen alarmen gevonden voor de komende week.", "No alarms found for the coming week.", "No se encontraron alarmas para la próxima semana.", "Nenhum alarme encontrado para a próxima semana.", "Keine Alarme für die kommende Woche gefunden.", "Aucune alarme trouvée pour la semaine à venir.", "Nessun allarme trovato per la prossima settimana.", "다음 주 알람이 없습니다.", "下周没有闹钟。", "来週のアラームは見つかりませんでした。",
            "Нет будильников на неделю.", "لا توجد منبهات للأسبوع القادم.", "आने वाले सप्ताह के लिए कोई अलार्म नहीं मिला।", "Gelecek hafta için alarm bulunamadı.", "Brak alarmów na nadchodzący tydzień.", "Tidak ada alarm untuk minggu depan.", "Немає будильників на тиждень.", "Không tìm thấy báo thức cho tuần tới."
        ),
        "today" to t(
            "Vandaag", "Today", "Hoy", "Hoje", "Heute", "Aujourd'hui", "Oggi", "오늘", "今天", "今日",
            "Сегодня", "اليوم", "आज", "Bugün", "Dziś", "Hari Ini", "Сьогодні", "Hôm nay"
        ),
        "tomorrow" to t(
            "Morgen", "Tomorrow", "Mañana", "Amanhã", "Morgen", "Demain", "Domani", "내일", "明天", "明日",
            "Завтра", "غداً", "कल", "Yarın", "Jutro", "Besok", "Завтра", "Ngày mai"
        ),

        // Calendar Settings (KalenderAlarmInstellingen)
        "ka_title" to t(
            "KalenderAlarm Instellingen", "CalendarAlarm Settings", "Ajustes CalendarAlarm", "Config. CalendarAlarm", "KalenderAlarm-Einstellungen", "Paramètres CalendarAlarm", "Impostazioni CalendarAlarm", "캘린더 알람 설정", "日历闹钟设置", "カレンダーアラーム設定",
            "Настр. CalendarAlarm", "إعدادات تنبيه التقويم", "कैलेंडर अलार्म सेटिंग्स", "Takvim Alarmı Ayarları", "Ustawienia CalendarAlarm", "Pengaturan CalendarAlarm", "Налашт. CalendarAlarm", "Cài đặt Báo thức Lịch"
        ),
        "ka_alarm_options" to t(
            "Alarm triggers", "Alarm Triggers", "Activadores de alarma", "Gatilhos de alarme", "Alarm-Auslöser", "Déclencheurs d'alarme", "Trigger allarme", "알람 트리거", "闹钟触发器", "アラームトリガー",
            "Триггеры будильника", "مشغلات التنبيه", "अलार्म ट्रिगर", "Alarm Tetikleyicileri", "Wyzwalacze alarmu", "Pemicu Alarm", "Тригери будильника", "Kích hoạt báo thức"
        ),
        "ka_ha" to t(
            "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant",
            "Home Assistant", "مساعد المنزل", "होम असिस्टेंट", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant"
        ),
        "ka_trigger_onboarding_title" to t(
            "Stel Agenda Trigger In", "Set up calendar trigger", "Configurar disparador", "Configurar gatilho", "Kalenderauslöser einrichten", "Configurer le déclencheur", "Imposta trigger calendario", "캘린더 트리거 설정", "设置日历触发器", "カレンダートリガーを設定",
            "Настроить триггер", "إعداد مشغّل", "कैलेंडर ट्रिगर सेट करें", "Takvim tetikleyicisini ayarla", "Ustaw wyzwalacz", "Atur pemicu kalender", "Налаштувати тригер", "Thiết lập kích hoạt"
        ),
        "ka_trigger_onboarding_cta" to t(
            "Selecteer trigger", "Select trigger", "Elegir disparador", "Selecionar gatilho", "Trigger wählen", "Choisir le déclencheur", "Seleziona trigger", "트리거 선택", "选择触发器", "トリガーを選択",
            "Выбрать триггер", "اختر المشغّل", "ट्रिगर चुनें", "Tetikleyici seç", "Wybierz wyzwalacz", "Pilih pemicu", "Обрати тригер", "Chọn kích hoạt"
        ),

        // Agenda-pagina (achtergrond sync instellingen)
        "nav_agenda" to t(
            "Agenda", "Calendar", "Calendario", "Calendário", "Kalender", "Calendrier", "Calendario", "캘린더", "日历", "カレンダー",
            "Календарь", "التقويم", "कैलेंडर", "Takvim", "Kalendarz", "Kalender", "Календар", "Lịch"
        ),
        "agenda_auto_sync_title" to t(
            "Automatisch synchroniseren", "Automatic sync", "Sincronización automática", "Sincronização automática", "Automatische Synchronisierung", "Synchronisation automatique", "Sincronizzazione automatica", "자동 동기화", "自动同步", "自動同期",
            "Автоматическая синхронизация", "المزامنة التلقائية", "स्वचालित सिंक", "Otomatik senkronizasyon", "Automatyczna synchronizacja", "Sinkronisasi otomatis", "Автоматична синхронізація", "Đồng bộ tự động"
        ),
        "agenda_auto_sync_desc" to t(
            "Controleert de agenda op de achtergrond, ook als de app gesloten is.", "Checks your calendar in the background, even when the app is closed.", "Comprueba tu calendario en segundo plano, incluso con la app cerrada.", "Verifica o seu calendário em segundo plano, mesmo com a app fechada.", "Prüft deinen Kalender im Hintergrund, auch wenn die App geschlossen ist.", "Vérifie votre calendrier en arrière-plan, même si l'application est fermée.", "Controlla il calendario in background, anche ad app chiusa.", "앱이 닫혀 있어도 백그라운드에서 캘린더를 확인합니다.", "即使应用已关闭，也会在后台检查日历。", "アプリを閉じていても、バックグラウンドでカレンダーを確認します。",
            "Проверяет календарь в фоновом режиме, даже если приложение закрыто.", "يتحقق من التقويم في الخلفية، حتى عند إغلاق التطبيق.", "ऐप बंद होने पर भी बैकग्राउंड में कैलेंडर जांचता है।", "Uygulama kapalıyken bile takvimi arka planda kontrol eder.", "Sprawdza kalendarz w tle, nawet gdy aplikacja jest zamknięta.", "Memeriksa kalender di latar belakang, meski aplikasi ditutup.", "Перевіряє календар у фоновому режимі, навіть коли застосунок закрито.", "Kiểm tra lịch ở chế độ nền, ngay cả khi ứng dụng đã đóng."
        ),
        "agenda_interval_title" to t(
            "Sync-interval", "Sync interval", "Intervalo de sincronización", "Intervalo de sincronização", "Sync-Intervall", "Intervalle de synchronisation", "Intervallo di sincronizzazione", "동기화 간격", "同步间隔", "同期間隔",
            "Интервал синхронизации", "فاصل المزامنة", "सिंक अंतराल", "Senkronizasyon aralığı", "Interwał synchronizacji", "Interval sinkronisasi", "Інтервал синхронізації", "Khoảng thời gian đồng bộ"
        ),
        "agenda_interval_desc" to t(
            "Hoe vaak de agenda gecheckt wordt (minimum 15 min).", "How often the calendar is checked (minimum 15 min).", "Con qué frecuencia se revisa el calendario (mínimo 15 min).", "Com que frequência o calendário é verificado (mínimo 15 min).", "Wie oft der Kalender geprüft wird (mindestens 15 Min).", "Fréquence de vérification du calendrier (minimum 15 min).", "Ogni quanto viene controllato il calendario (minimo 15 min).", "캘린더를 확인하는 빈도(최소 15분).", "检查日历的频率（最少15分钟）。", "カレンダーを確認する頻度（最短15分）。",
            "Как часто проверяется календарь (минимум 15 мин).", "عدد مرات فحص التقويم (15 دقيقة على الأقل).", "कैलेंडर कितनी बार जांचा जाता है (न्यूनतम 15 मिनट)।", "Takvimin ne sıklıkla kontrol edileceği (en az 15 dk).", "Jak często sprawdzany jest kalendarz (minimum 15 min).", "Seberapa sering kalender diperiksa (minimal 15 menit).", "Як часто перевіряється календар (мінімум 15 хв).", "Tần suất kiểm tra lịch (tối thiểu 15 phút)."
        ),
        "agenda_battery_title" to t(
            "Batterijoptimalisatie uitzetten", "Disable battery optimization", "Desactivar optimización de batería", "Desativar otimização de bateria", "Akku-Optimierung deaktivieren", "Désactiver l'optimisation de la batterie", "Disattiva ottimizzazione batteria", "배터리 최적화 끄기", "关闭电池优化", "バッテリー最適化を無効にする",
            "Отключить оптимизацию батареи", "تعطيل تحسين البطارية", "बैटरी अनुकूलन बंद करें", "Pil optimizasyonunu kapat", "Wyłącz optymalizację baterii", "Nonaktifkan pengoptimalan baterai", "Вимкнути оптимізацію батареї", "Tắt tối ưu hóa pin"
        ),
        "agenda_battery_desc" to t(
            "Voorkomt dat het toestel de achtergrond-check uitschakelt.", "Prevents the device from disabling the background check.", "Evita que el dispositivo desactive la comprobación en segundo plano.", "Evita que o dispositivo desative a verificação em segundo plano.", "Verhindert, dass das Gerät die Hintergrundprüfung deaktiviert.", "Empêche l'appareil de désactiver la vérification en arrière-plan.", "Evita che il dispositivo disattivi il controllo in background.", "기기가 백그라운드 확인을 비활성화하지 않도록 합니다.", "防止设备关闭后台检查。", "端末がバックグラウンド確認を無効にするのを防ぎます。",
            "Не позволяет устройству отключать фоновую проверку.", "يمنع الجهاز من تعطيل الفحص في الخلفية.", "डिवाइस को बैकग्राउंड जांच बंद करने से रोकता है।", "Cihazın arka plan kontrolünü kapatmasını önler.", "Zapobiega wyłączaniu sprawdzania w tle przez urządzenie.", "Mencegah perangkat menonaktifkan pemeriksaan latar belakang.", "Не дозволяє пристрою вимикати фонову перевірку.", "Ngăn thiết bị tắt việc kiểm tra nền."
        ),
        "agenda_battery_button" to t(
            "Batterij-instellingen openen", "Open battery settings", "Abrir ajustes de batería", "Abrir definições de bateria", "Akkueinstellungen öffnen", "Ouvrir les paramètres de batterie", "Apri impostazioni batteria", "배터리 설정 열기", "打开电池设置", "バッテリー設定を開く",
            "Открыть настройки батареи", "فتح إعدادات البطارية", "बैटरी सेटिंग्स खोलें", "Pil ayarlarını aç", "Otwórz ustawienia baterii", "Buka pengaturan baterai", "Відкрити налаштування батареї", "Mở cài đặt pin"
        ),
        "agenda_appinfo_button" to t(
            "App-info openen (accuverbruik)", "Open app info (battery usage)", "Abrir info de la app (uso de batería)", "Abrir info da app (uso de bateria)", "App-Info öffnen (Akkunutzung)", "Ouvrir les infos de l'app (utilisation batterie)", "Apri info app (utilizzo batteria)", "앱 정보 열기 (배터리 사용량)", "打开应用信息（电池使用情况）", "アプリ情報を開く（バッテリー使用状況）",
            "Открыть информацию о приложении (использование батареи)", "فتح معلومات التطبيق (استخدام البطارية)", "ऐप जानकारी खोलें (बैटरी उपयोग)", "Uygulama bilgisini aç (pil kullanımı)", "Otwórz informacje o aplikacji (zużycie baterii)", "Buka info aplikasi (penggunaan baterai)", "Відкрити інформацію про застосунок (використання батареї)", "Mở thông tin ứng dụng (mức sử dụng pin)"
        ),
        "agenda_appinfo_title" to t(
            "Achtergrond-check beschermen", "Protect background check", "Proteger la comprobación en segundo plano", "Proteger a verificação em segundo plano", "Hintergrundprüfung schützen", "Protéger la vérification en arrière-plan", "Proteggi il controllo in background", "백그라운드 확인 보호", "保护后台检查", "バックグラウンド確認を保護",
            "Защитить фоновую проверку", "حماية الفحص في الخلفية", "बैकग्राउंड जांच सुरक्षित करें", "Arka plan kontrolünü koru", "Chroń sprawdzanie w tle", "Lindungi pemeriksaan latar belakang", "Захистити фонову перевірку", "Bảo vệ việc kiểm tra nền"
        ),
        "agenda_appinfo_desc" to t(
            "Zet batterijbesparing uit voor deze app.", "Turn off battery saving for this app.", "Desactiva el ahorro de batería para esta app.", "Desative a poupança de bateria para esta app.", "Akkusparen für diese App deaktivieren.", "Désactivez l'économie de batterie pour cette app.", "Disattiva il risparmio batteria per questa app.", "이 앱의 배터리 절약을 꺼주세요.", "为此应用关闭省电模式。", "このアプリのバッテリー節約をオフにしてください。",
            "Отключите энергосбережение для этого приложения.", "أوقف توفير البطارية لهذا التطبيق.", "इस ऐप के लिए बैटरी सेवर बंद करें।", "Bu uygulama için pil tasarrufunu kapatın.", "Wyłącz oszczędzanie baterii dla tej aplikacji.", "Matikan penghemat baterai untuk aplikasi ini.", "Вимкніть заощадження батареї для цього застосунку.", "Tắt tiết kiệm pin cho ứng dụng này."
        ),
        "agenda_boot_note" to t(
            "Als het toestel volledig uit staat, kan geen enkele app op de achtergrond werken. Zodra het weer aan staat, hervat de agenda-check automatisch.", "When the device is fully powered off, no app can run in the background. Once it's back on, the calendar check resumes automatically.", "Cuando el dispositivo está completamente apagado, ninguna app puede ejecutarse en segundo plano. Al encenderlo de nuevo, la comprobación del calendario se reanuda automáticamente.", "Quando o dispositivo está totalmente desligado, nenhuma app pode ser executada em segundo plano. Assim que for ligado novamente, a verificação do calendário é retomada automaticamente.", "Wenn das Gerät vollständig ausgeschaltet ist, kann keine App im Hintergrund laufen. Sobald es wieder eingeschaltet ist, wird die Kalenderprüfung automatisch fortgesetzt.", "Lorsque l'appareil est complètement éteint, aucune application ne peut fonctionner en arrière-plan. Dès qu'il est rallumé, la vérification du calendrier reprend automatiquement.", "Quando il dispositivo è completamente spento, nessuna app può funzionare in background. Una volta riacceso, il controllo del calendario riprende automaticamente.", "기기가 완전히 꺼져 있으면 어떤 앱도 백그라운드에서 실행될 수 없습니다. 다시 켜지면 캘린더 확인이 자동으로 재개됩니다.", "设备完全关机时，任何应用都无法在后台运行。设备重新开机后，日历检查会自动恢复。", "端末が完全に電源オフの場合、どのアプリもバックグラウンドで動作できません。再度電源を入れると、カレンダー確認は自動的に再開されます。",
            "Если устройство полностью выключено, ни одно приложение не может работать в фоне. После включения проверка календаря возобновляется автоматически.", "عند إيقاف تشغيل الجهاز بالكامل، لا يمكن لأي تطبيق العمل في الخلفية. بمجرد تشغيله مرة أخرى، يستأنف فحص التقويم تلقائيًا.", "जब डिवाइस पूरी तरह से बंद हो, तो कोई भी ऐप बैकग्राउंड में नहीं चल सकता। वापस चालू होते ही, कैलेंडर जांच अपने आप फिर से शुरू हो जाती है।", "Cihaz tamamen kapalıyken hiçbir uygulama arka planda çalışamaz. Tekrar açıldığında takvim kontrolü otomatik olarak devam eder.", "Gdy urządzenie jest całkowicie wyłączone, żadna aplikacja nie może działać w tle. Po ponownym włączeniu sprawdzanie kalendarza wznawia się automatycznie.", "Saat perangkat benar-benar mati, tidak ada aplikasi yang dapat berjalan di latar belakang. Setelah dinyalakan kembali, pemeriksaan kalender akan dilanjutkan secara otomatis.", "Коли пристрій повністю вимкнено, жоден застосунок не може працювати у фоні. Щойно він знову увімкнеться, перевірка календаря відновиться автоматично.", "Khi thiết bị tắt hoàn toàn, không ứng dụng nào có thể chạy ở chế độ nền. Khi bật lại, việc kiểm tra lịch sẽ tự động tiếp tục."
        ),

        // KalenderAlarm: agenda-toegang / provider (gate card + overlay)
        "calendar_gate_title_permission" to t(
            "Agenda lezen voor KalenderAlarm", "Calendar access for Calendar Alarm", "Lectura del calendario para Calendar Alarm", "Leitura de calendário para Calendar Alarm", "Kalenderzugriff für Kalender-Wecker", "Accès au calendrier pour Calendar Alarm", "Accesso al calendario per Calendar Alarm", "캘린더 알람을 위한 캘린더 읽기", "日历闹钟的日历读取权限", "カレンダーアラームのカレンダー読み取り",
            "Доступ к календарю для Calendar Alarm", "الوصول إلى التقويم لتنبيه التقويم", "कैलेंडर अलार्म के लिए कैलेंडर पढ़ना", "Takvim Alarmı için takvim erişimi", "Dostęp do kalendarza dla Calendar Alarm", "Akses kalender untuk Calendar Alarm", "Доступ до календаря для Calendar Alarm", "Quyền đọc lịch cho báo thức lịch"
        ),
        "calendar_gate_title_unavailable" to t(
            "Agenda niet toegankelijk", "Calendar not accessible", "Calendario no accesible", "Calendário inacessível", "Kalender nicht zugänglich", "Calendrier inaccessible", "Calendario non accessibile", "캘린더에 액세스할 수 없음", "无法访问日历", "カレンダーにアクセスできません",
            "Календарь недоступен", "التقويم غير متاح", "कैलेंडर उपलब्ध नहीं", "Takvime erişilemiyor", "Kalendarz niedostępny", "Kalender tidak dapat diakses", "Календар недоступний", "Không truy cập được lịch"
        ),
        "calendar_gate_body_permission" to t(
            "KalenderAlarm heeft toegang tot je agenda nodig om afspraken te lezen en alarmen te plannen.",
            "Calendar Alarm needs access to your calendar to read appointments and schedule alarms.",
            "Calendar Alarm necesita acceso a tu calendario para leer citas y programar alarmas.",
            "O Calendar Alarm precisa de acesso ao calendário para ler compromissos e agendar alarmes.",
            "Kalender-Wecker benötigt Zugriff auf deinen Kalender, um Termine zu lesen und Alarme zu planen.",
            "Calendar Alarm a besoin d’accéder à votre calendrier pour lire les rendez-vous et planifier les alarmes.",
            "Calendar Alarm richiede l’accesso al calendario per leggere gli appuntamenti e programmare le sveglie.",
            "캘린더 알람은 일정을 읽고 알람을 예약하려면 캘린더에 대한 액세스가 필요합니다.",
            "日历闹钟需要访问您的日历以读取日程并安排闹钟。",
            "カレンダーアラームは予定を読み取りアラームを予約するためにカレンダーへのアクセスが必要です。",
            "Calendar Alarm нужен доступ к календарю, чтобы читать события и планировать будильники.",
            "يحتاج Calendar Alarm إلى الوصول إلى التقويم لقراءة المواعيد وجدولة المنبهات.",
            "कैलेंडर अलार्म को अपॉइंटमेंट पढ़ने और अलार्म शेड्यूल करने के लिए आपके कैलेंडर तक पहुँच चाहिए।",
            "Takvim Alarmı randevuları okumak ve alarmları planlamak için takviminize erişime ihtiyaç duyar.",
            "Calendar Alarm wymaga dostępu do kalendarza, by czytać spotkania i planować alarmy.",
            "Calendar Alarm memerlukan akses ke kalender Anda untuk membaca janji temu dan menjadwalkan alarm.",
            "Calendar Alarm потрібен доступ до вашого календаря, щоб читати події й планувати будильники.",
            "Calendar Alarm cần quyền truy cập lịch để đọc lịch hẹn và lên lịch báo thức."
        ),
        "calendar_gate_body_unavailable" to t(
            "Je agenda is nu niet leesbaar voor deze app. Controleer account- en agenda-sync in de systeeminstellingen.",
            "Your calendar cannot be read right now. Check account and calendar sync in system settings.",
            "Tu calendario no se puede leer ahora. Revisa la cuenta y la sincronización en ajustes del sistema.",
            "Seu calendário não pode ser lido agora. Verifique conta e sincronização nas configurações do sistema.",
            "Dein Kalender kann gerade nicht gelesen werden. Prüfe Konto- und Kalendersync in den Systemeinstellungen.",
            "Votre calendrier n’est pas lisible pour l’instant. Vérifiez le compte et la synchro dans les réglages système.",
            "Il calendario non è leggibile ora. Controlla account e sincronizzazione nelle impostazioni di sistema.",
            "지금은 캘린더를 읽을 수 없습니다. 시스템 설정에서 계정 및 캘린더 동기화를 확인하세요.",
            "当前无法读取日历。请在系统设置中检查账户与日历同步。",
            "現在カレンダーを読み取れません。システム設定でアカウントと同期を確認してください。",
            "Сейчас календарь недоступен для чтения. Проверьте аккаунт и синхронизацию в настройках системы.",
            "لا يمكن قراءة التقويم الآن. تحقق من الحساب والمزامنة في إعدادات النظام.",
            "अभी कैलेंडर नहीं पढ़ा जा सकता। सिस्टम सेटिंग्स में खाता और सिंक जाँचें।",
            "Takvim şu an okunamıyor. Sistem ayarlarında hesap ve takvim senkronizasyonunu kontrol edin.",
            "Kalendarz nie jest teraz czytelny. Sprawdź konto i synchronizację w ustawieniach systemu.",
            "Kalender tidak dapat dibaca sekarang. Periksa akun dan sinkronisasi di pengaturan sistem.",
            "Зараз календар недоступний для читання. Перевірте обліковий запис і синхронізацію в системних налаштуваннях.",
            "Hiện không đọc được lịch. Kiểm tra tài khoản và đồng bộ trong cài đặt hệ thống."
        ),
        "calendar_grant_access" to t(
            "Agenda-toegang geven", "Grant calendar access", "Conceder acceso al calendario", "Conceder acesso ao calendário", "Kalenderzugriff erlauben", "Autoriser l’accès au calendrier", "Consenti accesso al calendario", "캘린더 액세스 허용", "授予日历访问权限", "カレンダーへのアクセスを許可",
            "Разрешить доступ к календарю", "منح الوصول إلى التقويم", "कैलेंडर पहुँच दें", "Takvim erişimine izin ver", "Zezwól na dostęp do kalendarza", "Berikan akses kalender", "Надати доступ до календаря", "Cấp quyền truy cập lịch"
        ),
        "calendar_open_app_settings" to t(
            "App-instellingen", "App settings", "Ajustes de la app", "Configurações do app", "App-Einstellungen", "Paramètres de l’app", "Impostazioni app", "앱 설정", "应用设置", "アプリの設定",
            "Настройки приложения", "إعدادات التطبيق", "ऐप सेटिंग्स", "Uygulama ayarları", "Ustawienia aplikacji", "Pengaturan aplikasi", "Налаштування застосунку", "Cài đặt ứng dụng"
        ),
        "calendar_retry" to t(
            "Opnieuw proberen", "Retry", "Reintentar", "Tentar novamente", "Erneut versuchen", "Réessayer", "Riprova", "다시 시도", "重试", "再試行",
            "Повторить", "إعادة المحاولة", "पुनः प्रयास करें", "Yeniden dene", "Spróbuj ponownie", "Coba lagi", "Повторити", "Thử lại"
        ),

        // Alarm-instellingen: triggers wanneer agenda ontbreekt / leeg
        "alarm_triggers_permission_rationale" to t(
            "Agenda lezen is nodig om KalenderAlarm-triggers uit je agenda te laden.",
            "Read calendar permission is needed to load Calendar Alarm triggers from your calendar.",
            "Se necesita leer el calendario para cargar los activadores de Calendar Alarm desde tu agenda.",
            "É necessário ler o calendário para carregar os gatilhos do Calendar Alarm da sua agenda.",
            "Kalenderlesezugriff wird benötigt, um Kalender-Wecker-Auslöser aus dem Kalender zu laden.",
            "La lecture du calendrier est nécessaire pour charger les déclencheurs Calendar Alarm depuis votre agenda.",
            "Serve l’accesso in lettura al calendario per caricare i trigger di Calendar Alarm.",
            "캘린더 알람 트리거를 불러오려면 캘린더 읽기 권한이 필요합니다.",
            "需要读取日历才能从日历加载日历闹钟触发器。",
            "カレンダーアラームのトリガーを読み込むにはカレンダーの読み取りが必要です。",
            "Нужно разрешение на чтение календаря, чтобы загрузить триггеры Calendar Alarm.",
            "يلزم إذن قراءة التقويم لتحميل مشغلات Calendar Alarm من التقويم.",
            "कैलेंडर अलार्म ट्रिगर लोड करने के लिए कैलेंडर पढ़ने की अनुमति चाहिए।",
            "Takvim Alarmı tetikleyicilerini yüklemek için takvim okuma izni gerekir.",
            "Aby wczytać wyzwalacze Calendar Alarm, potrzebny jest dostęp do odczytu kalendarza.",
            "Diperlukan izin baca kalender untuk memuat pemicu Calendar Alarm.",
            "Потрібен доступ на читання календаря, щоб завантажити тригери Calendar Alarm.",
            "Cần quyền đọc lịch để tải kích hoạt Calendar Alarm."
        ),
        "alarm_triggers_unavailable_body" to t(
            "De agenda is op dit moment niet leesbaar op dit toestel. Geef zo nodig extra rechten in de instellingen en tik daarna op Opnieuw proberen.",
            "Your calendar cannot be read on this device right now. Grant any extra permissions in settings, then tap Retry.",
            "Tu calendario no se puede leer ahora en este dispositivo. Concede permisos adicionales en ajustes y luego pulsa Reintentar.",
            "Seu calendário não pode ser lido neste aparelho agora. Conceda permissões extras nas configurações e toque em Tentar novamente.",
            "Dein Kalender kann auf diesem Gerät gerade nicht gelesen werden. Erteile ggf. zusätzliche Rechte in den Einstellungen und tippe auf Erneut versuchen.",
            "Votre calendrier n’est pas lisible sur cet appareil pour l’instant. Accordez les droits nécessaires dans les réglages, puis appuyez sur Réessayer.",
            "Il calendario non è leggibile su questo dispositivo. Concedi eventuali permessi aggiuntivi nelle impostazioni, poi tocca Riprova.",
            "지금은 이 기기에서 캘린더를 읽을 수 없습니다. 설정에서 필요한 권한을 허용한 뒤 다시 시도를 누르세요.",
            "当前无法在此设备上读取日历。请在设置中授予额外权限，然后点击重试。",
            "この端末では今カレンダーを読めません。設定で追加の権限を付与してから再試行をタップしてください。",
            "Сейчас календарь не читается на этом устройстве. Выдайте дополнительные разрешения в настройках и нажмите Повторить.",
            "لا يمكن قراءة التقويم على هذا الجهاز الآن. امنح الأذونات الإضافية في الإعدادات ثم اضغط إعادة المحاولة.",
            "अभी इस डिवाइस पर कैलेंडर नहीं पढ़ा जा सकता। सेटिंग्स में अतिरिक्त अनुमतियाँ दें, फिर पुनः प्रयास करें पर टैप करें।",
            "Takvim şu an bu cihazda okunamıyor. Ayarlarda ek izinleri verin, ardından Yeniden dene’ye dokunun.",
            "Kalendarz nie jest teraz czytelny na tym urządzeniu. Nadaj dodatkowe uprawnienia w ustawieniach, potem stuknij Spróbuj ponownie.",
            "Kalender tidak dapat dibaca di perangkat ini sekarang. Berikan izin tambahan di pengaturan, lalu ketuk Coba lagi.",
            "Зараз календар на цьому пристрої не читається. Надайте додаткові дозволи в налаштуваннях, потім натисніть Повторити.",
            "Hiện không đọc được lịch trên thiết bị này. Cấp thêm quyền trong cài đặt, rồi chạm Thử lại."
        ),
        "alarm_no_calendars_help" to t(
            "Geen agenda's gevonden. Voeg een agenda toe op je telefoon en zorg dat agenda-sync aan staat.",
            "No calendars found. Add a calendar on your phone and make sure calendar sync is enabled.",
            "No se encontraron calendarios. Añade un calendario en el teléfono y activa la sincronización.",
            "Nenhum calendário encontrado. Adicione um calendário no telefone e ative a sincronização.",
            "Keine Kalender gefunden. Füge einen Kalender auf dem Telefon hinzu und aktiviere die Synchronisation.",
            "Aucun calendrier trouvé. Ajoutez un calendrier sur le téléphone et activez la synchronisation.",
            "Nessun calendario trovato. Aggiungi un calendario sul telefono e attiva la sincronizzazione.",
            "캘린더가 없습니다. 휴대전화에 캘린더를 추가하고 동기화를 켜세요.",
            "未找到日历。请在手机上添加日历并开启同步。",
            "カレンダーが見つかりません。端末にカレンダーを追加し、同期を有効にしてください。",
            "Календари не найдены. Добавьте календарь на телефоне и включите синхронизацию.",
            "لم يُعثر على تقويمات. أضف تقويمًا على الهاتف وفعّل المزامنة.",
            "कोई कैलेंडर नहीं मिला। फ़ोन पर कैलेंडर जोड़ें और सिंक चालू रखें।",
            "Takvim bulunamadı. Telefona takvim ekleyin ve senkronizasyonu açın.",
            "Nie znaleziono kalendarzy. Dodaj kalendarz w telefonie i włącz synchronizację.",
            "Tidak ada kalender. Tambahkan kalender di ponsel dan aktifkan sinkronisasi.",
            "Календарів не знайдено. Додайте календар на телефоні й увімкніть синхронізацію.",
            "Không thấy lịch nào. Thêm lịch trên điện thoại và bật đồng bộ."
        ),

        // Eerste start: permissie-checklist (MainActivity)
        "permissions_title" to t(
            "Toestemmingen", "Permissions", "Permisos", "Permissões", "Berechtigungen", "Autorisations", "Autorizzazioni", "권한", "权限", "権限",
            "Разрешения", "الأذونات", "अनुमतियाँ", "İzinler", "Uprawnienia", "Izin", "Дозволи", "Quyền"
        ),
        "permissions_intro" to t(
            "Exacte alarmen zijn nodig voor betrouwbare timing. De overige opties kun je overslaan en later aanzetten.",
            "Exact alarms are needed for reliable timing. The other options can be skipped and enabled later.",
            "Las alarmas exactas son necesarias para una hora fiable. Las demás opciones se pueden omitir y activar luego.",
            "Alarmes exatos são necessários para horário confiável. As outras opções podem ser ignoradas e ativadas depois.",
            "Exakte Alarme sind für zuverlässiges Timing nötig. Die anderen Optionen kannst du überspringen und später aktivieren.",
            "Les alarmes exactes sont nécessaires pour un horaire fiable. Les autres options peuvent être ignorées puis activées plus tard.",
            "Gli allarmi esatti servono per un orario affidabile. Le altre opzioni possono essere saltate e attivate dopo.",
            "정확한 알람은 안정적인 시간 맞춤에 필요합니다. 다른 옵션은 건너뛰고 나중에 켤 수 있습니다.",
            "精确闹钟用于可靠计时。其他选项可跳过并稍后启用。",
            "正確なアラームは確実な時刻動作に必要です。他のオプションはスキップして後で有効にできます。",
            "Точные будильники нужны для надёжного времени. Остальные параметры можно пропустить и включить позже.",
            "المنبهات الدقيقة مطلوبة للتوقيت الموثوق. يمكن تخطي الخيارات الأخرى وتفعيلها لاحقًا.",
            "विश्वसनीय समय के लिए सटीक अलार्म आवश्यक हैं। बाकी विकल्प छोड़े जा सकते हैं और बाद में चालू किए जा सकते हैं।",
            "Güvenilir zamanlama için kesin alarmlar gereklidir. Diğer seçenekler atlanıp sonra açılabilir.",
            "Dokładne alarmy są potrzebne do niezawodnego czasu. Pozostałe opcje można pominąć i włączyć później.",
            "Alarm tepat diperlukan untuk waktu yang andal. Opsi lain dapat dilewati dan diaktifkan nanti.",
            "Точні будильники потрібні для надійного часу. Інші параметри можна пропустити й увімкнути пізніше.",
            "Báo thức chính xác cần cho thời gian đáng tin cậy. Các tùy chọn khác có thể bỏ qua và bật sau."
        ),
        "permissions_section_required" to t(
            "Vereist", "Required", "Obligatorio", "Obrigatório", "Erforderlich", "Obligatoire", "Obbligatorio", "필수", "必需", "必須",
            "Обязательно", "مطلوب", "आवश्यक", "Gerekli", "Wymagane", "Wajib", "Обов’язково", "Bắt buộc"
        ),
        "permissions_section_optional" to t(
            "Optioneel", "Optional", "Opcional", "Opcional", "Optional", "Facultatif", "Facoltativo", "선택", "可选", "任意",
            "Необязательно", "اختياري", "वैकल्पिक", "İsteğe bağlı", "Opcjonalne", "Opsional", "Необов’язково", "Tùy chọn"
        ),
        "permissions_optional_features_explanation" to t(
            "Deze functies maken alarmen zichtbaarder, maar zijn niet verplicht. Normale Android-meldingen blijven werken.",
            "These features make alarms more visible, but they are not required. Normal Android notifications keep working.",
            "Estas funciones hacen que las alarmas sean más visibles, pero no son obligatorias. Las notificaciones normales de Android siguen funcionando.",
            "Estas funções tornam os alarmes mais visíveis, mas não são obrigatórias. As notificações normais do Android continuam funcionando.",
            "Diese Funktionen machen Alarme sichtbarer, sind aber nicht erforderlich. Normale Android-Benachrichtigungen funktionieren weiter.",
            "Ces fonctions rendent les alarmes plus visibles, mais elles ne sont pas obligatoires. Les notifications Android normales continuent de fonctionner.",
            "Queste funzioni rendono gli allarmi più visibili, ma non sono obbligatorie. Le normali notifiche Android continuano a funzionare.",
            "이 기능은 알람을 더 잘 보이게 하지만 필수는 아닙니다. 일반 Android 알림은 계속 작동합니다.",
            "这些功能让闹钟更醒目，但不是必需的。普通 Android 通知仍会工作。",
            "これらの機能はアラームを見やすくしますが、必須ではありません。通常の Android 通知は引き続き動作します。",
            "Эти функции делают будильники заметнее, но не обязательны. Обычные уведомления Android продолжают работать.",
            "تجعل هذه الميزات المنبهات أوضح، لكنها ليست مطلوبة. تستمر إشعارات Android العادية في العمل.",
            "ये सुविधाएं अलार्म को अधिक स्पष्ट बनाती हैं, लेकिन आवश्यक नहीं हैं। सामान्य Android सूचनाएं काम करती रहेंगी।",
            "Bu özellikler alarmları daha görünür yapar, ancak zorunlu değildir. Normal Android bildirimleri çalışmaya devam eder.",
            "Te funkcje zwiększają widoczność alarmów, ale nie są wymagane. Zwykłe powiadomienia Androida nadal działają.",
            "Fitur ini membuat alarm lebih terlihat, tetapi tidak wajib. Notifikasi Android normal tetap berfungsi.",
            "Ці функції роблять будильники помітнішими, але не є обов'язковими. Звичайні сповіщення Android працюють далі.",
            "Các tính năng này giúp báo thức dễ thấy hơn, nhưng không bắt buộc. Thông báo Android bình thường vẫn hoạt động."
        ),
        "permissions_optional_calendar_explanation" to t(
            "Alleen nodig om agenda-afspraken als alarm te gebruiken (KalenderAlarm / Google Calendar-sync op dit toestel).",
            "Only needed to use calendar events as alarms (Calendar Alarm / Google Calendar sync on this device).",
            "Solo necesario para usar citas del calendario como alarmas (Calendar Alarm / sincronización de Google Calendar en este dispositivo).",
            "Só é necessário para usar eventos do calendário como alarmes (Calendar Alarm / sincronização do Google Agenda neste aparelho).",
            "Nur nötig, um Kalendertermine als Alarme zu nutzen (Kalender-Wecker / Google-Kalender-Sync auf diesem Gerät).",
            "Uniquement nécessaire pour utiliser les événements comme alarmes (Calendar Alarm / synchro Google Agenda sur cet appareil).",
            "Serve solo per usare gli eventi del calendario come sveglie (Calendar Alarm / sync Google Calendar su questo dispositivo).",
            "캘린더 일정을 알람으로 쓰려면 필요합니다(캘린더 알람 / 이 기기의 Google 캘린더 동기화).",
            "仅在使用日历日程作为闹钟时需要（日历闹钟 / 本机 Google 日历同步）。",
            "カレンダーの予定をアラームに使う場合に必要です（カレンダーアラーム / この端末での Google カレンダー同期）。",
            "Нужно только чтобы использовать события календаря как будильники (Calendar Alarm / синхронизация Google Календаря на устройстве).",
            "مطلوب فقط لاستخدام أحداث التقويم كمنبهات (تنبيه التقويم / مزامنة Google Calendar على هذا الجهاز).",
            "कैलेंडर इवेंट को अलार्म के रूप में उपयोग करने के लिए (कैलेंडर अलार्म / इस डिवाइस पर Google कैलेंडर सिंक)।",
            "Takvim etkinliklerini alarm olarak kullanmak için gerekir (Takvim Alarmı / bu cihazda Google Takvim senkronizasyonu).",
            "Potrzebne tylko do używania wydarzeń kalendarza jako alarmów (Calendar Alarm / synchronizacja Google Calendar na tym urządzeniu).",
            "Hanya diperlukan untuk memakai acara kalender sebagai alarm (Calendar Alarm / sinkron Google Calendar di perangkat ini).",
            "Потрібно лише для використання подій календаря як будильників (Calendar Alarm / синхронізація Google Календаря на цьому пристрої).",
            "Chỉ cần khi dùng sự kiện lịch làm báo thức (Calendar Alarm / đồng bộ Google Calendar trên thiết bị này)."
        ),
        "permission_read_calendar" to t(
            "Agenda lezen", "Read calendar", "Leer calendario", "Ler calendário", "Kalender lesen", "Lire le calendrier", "Leggi calendario", "캘린더 읽기", "读取日历", "カレンダーを読む",
            "Чтение календаря", "قراءة التقويم", "कैलेंडर पढ़ें", "Takvimi oku", "Odczyt kalendarza", "Baca kalender", "Читання календаря", "Đọc lịch"
        ),
        "permission_alarms_and_reminders" to t(
            "Wekkers en Herinneringen", "Alarms & reminders", "Alarmas y recordatorios", "Alarmes e lembretes", "Wecker & Erinnerungen", "Alarmes et rappels", "Sveglie e promemoria", "알람 및 미리 알림", "闹钟和提醒", "アラームとリマインダー",
            "Будильники и напоминания", "المنبهات والتذكيرات", "अलार्म और अनुस्मारक", "Alarmlar ve hatırlatıcılar", "Alarmy i przypomnienia", "Alarm dan pengingat", "Будильники та нагадування", "Báo thức và nhắc nhở"
        ),
        "permission_alarms_and_reminders_explanation" to t(
            "Nodig om alarmen precies op tijd te plannen. Zonder dit kunnen alarmen te laat of minder betrouwbaar afgaan.",
            "Needed to schedule alarms exactly on time. Without it, alarms may ring late or less reliably.",
            "Necesario para programar alarmas exactamente a tiempo. Sin esto, las alarmas pueden sonar tarde o con menos fiabilidad.",
            "Necessário para agendar alarmes exatamente na hora. Sem isso, os alarmes podem tocar tarde ou com menos confiabilidade.",
            "Nötig, um Alarme genau pünktlich zu planen. Ohne dies können Alarme verspätet oder weniger zuverlässig klingeln.",
            "Nécessaire pour programmer les alarmes exactement à l'heure. Sinon elles peuvent sonner en retard ou moins fiablement.",
            "Necessario per programmare gli allarmi esattamente in orario. Senza, possono suonare in ritardo o in modo meno affidabile.",
            "알람을 정확한 시간에 예약하는 데 필요합니다. 없으면 알람이 늦거나 덜 안정적으로 울릴 수 있습니다.",
            "用于准时安排闹钟。没有它，闹钟可能延迟或不太可靠。",
            "アラームを正確な時刻に設定するために必要です。無い場合、遅れたり信頼性が下がることがあります。",
            "Нужно для точного планирования будильников. Без этого они могут сработать позже или менее надёжно.",
            "مطلوب لجدولة المنبهات في وقتها بدقة. بدونه قد تتأخر أو تصبح أقل موثوقية.",
            "अलार्म को ठीक समय पर शेड्यूल करने के लिए आवश्यक। इसके बिना अलार्म देर से या कम भरोसेमंद बज सकते हैं।",
            "Alarmları tam zamanında planlamak için gerekir. Yoksa alarmlar geç veya daha az güvenilir çalabilir.",
            "Wymagane do planowania alarmów dokładnie na czas. Bez tego alarmy mogą dzwonić późno lub mniej niezawodnie.",
            "Diperlukan untuk menjadwalkan alarm tepat waktu. Tanpanya alarm dapat berbunyi terlambat atau kurang andal.",
            "Потрібно для точного планування будильників. Без цього вони можуть спрацювати пізно або менш надійно.",
            "Cần để lên lịch báo thức đúng giờ. Nếu không, báo thức có thể kêu trễ hoặc kém tin cậy hơn."
        ),
        "permission_full_screen_intent" to t(
            "Volledig scherm bij alarm", "Full screen on alarm", "Pantalla completa en alarma", "Ecrã inteiro no alarme", "Vollbild bei Alarm", "Plein écran pour l'alarme", "Schermo intero all'alarm", "알람 시 전체 화면", "闹钟全屏显示", "アラーム時の全画面表示",
            "Полноэкранный режим будильника", "ملء الشاشة عند المنبه", "अलार्म पर पूर्ण स्क्रीन", "Alarmda tam ekran", "Pełny ekran przy alarmie", "Layar penuh saat alarm", "Повноекранний режим будильника", "Toàn màn hình khi báo thức"
        ),
        "permission_full_screen_intent_explanation" to t(
            "Opent het alarmscherm direct vanaf de melding, ook op het vergrendelscherm als Android dit toestaat.",
            "Opens the alarm screen directly from the notification, including on the lock screen when Android allows it.",
            "Abre la pantalla de alarma directamente desde la notificación, incluso en la pantalla de bloqueo si Android lo permite.",
            "Abre o ecrã do alarme diretamente pela notificação, inclusive no bloqueio se o Android permitir.",
            "Öffnet den Alarmbildschirm direkt aus der Benachrichtigung, auch auf dem Sperrbildschirm, wenn Android es erlaubt.",
            "Ouvre l'écran d'alarme directement depuis la notification, y compris sur l'écran verrouillé si Android l'autorise.",
            "Apre la schermata dell'allarme direttamente dalla notifica, anche nel blocco schermo se Android lo consente.",
            "Android가 허용하면 잠금 화면에서도 알림에서 바로 알람 화면을 엽니다.",
            "如果 Android 允许，可从通知直接打开闹钟界面，包括锁屏时。",
            "Android が許可する場合、ロック画面でも通知から直接アラーム画面を開きます。",
            "Открывает экран будильника прямо из уведомления, включая экран блокировки, если Android разрешает.",
            "يفتح شاشة المنبه مباشرة من الإشعار، بما في ذلك شاشة القفل عندما يسمح Android بذلك.",
            "Android अनुमति दे तो लॉक स्क्रीन पर भी सूचना से सीधे अलार्म स्क्रीन खोलता है।",
            "Android izin verirse kilit ekranı dahil bildirimin içinden alarm ekranını açar.",
            "Otwiera ekran alarmu bezpośrednio z powiadomienia, także na ekranie blokady, jeśli Android pozwala.",
            "Membuka layar alarm langsung dari notifikasi, termasuk di layar kunci jika Android mengizinkan.",
            "Відкриває екран будильника прямо зі сповіщення, зокрема на екрані блокування, якщо Android дозволяє.",
            "Mở màn hình báo thức trực tiếp từ thông báo, kể cả trên màn hình khóa nếu Android cho phép."
        ),
        "fullscreen_notification" to t(
            "Fullscreen melding", "Fullscreen notification", "Notificación a pantalla completa", "Notificação em ecrã inteiro", "Vollbild-Benachrichtigung", "Notification plein écran", "Notifica a schermo intero", "전체 화면 알림", "全屏通知", "全画面通知",
            "Полноэкранное уведомление", "إشعار ملء الشاشة", "फ़ुलस्क्रीन सूचना", "Tam ekran bildirimi", "Powiadomienie pełnoekranowe", "Notifikasi layar penuh", "Повноекранне сповіщення", "Thông báo toàn màn hình"
        ),
        "permission_notifications" to t(
            "Notificaties", "Notifications", "Notificaciones", "Notificações", "Benachrichtigungen", "Notifications", "Notifiche", "알림", "通知", "通知",
            "Уведомления", "الإشعارات", "सूचनाएँ", "Bildirimler", "Powiadomienia", "Notifikasi", "Сповіщення", "Thông báo"
        ),
        "permission_notifications_explanation" to t(
            "Nodig om Android-meldingen te tonen op Android 13 en nieuwer.",
            "Needed to show Android notifications on Android 13 and newer.",
            "Necesario para mostrar notificaciones de Android en Android 13 y versiones posteriores.",
            "Necessário para mostrar notificações Android no Android 13 e mais recente.",
            "Erforderlich für Android-Benachrichtigungen unter Android 13 und neuer.",
            "Nécessaire pour afficher les notifications Android sur Android 13 et versions ultérieures.",
            "Necessario per mostrare notifiche Android su Android 13 e versioni successive.",
            "Android 13 이상에서 Android 알림을 표시하는 데 필요합니다.",
            "在 Android 13 及更高版本上显示 Android 通知需要此权限。",
            "Android 13 以降で Android 通知を表示するために必要です。",
            "Нужно для показа уведомлений Android на Android 13 и новее.",
            "مطلوب لعرض إشعارات Android على Android 13 والإصدارات الأحدث.",
            "Android 13 और नए संस्करणों पर Android सूचनाएं दिखाने के लिए आवश्यक।",
            "Android 13 ve üstünde Android bildirimlerini göstermek için gereklidir.",
            "Wymagane do pokazywania powiadomień Androida na Androidzie 13 i nowszym.",
            "Diperlukan untuk menampilkan notifikasi Android di Android 13 dan yang lebih baru.",
            "Потрібно для показу сповіщень Android на Android 13 і новіших.",
            "Cần để hiển thị thông báo Android trên Android 13 trở lên."
        ),
        "permission_draw_over_other_apps" to t(
            "Weergeven boven andere apps", "Display over other apps", "Mostrar sobre otras apps", "Mostrar sobre outros apps", "Über anderen Apps anzeigen", "Afficher par-dessus les autres apps", "Mostra sopra altre app", "다른 앱 위에 표시", "在其他应用上层显示", "他のアプリの上に表示",
            "Показывать поверх других приложений", "العرض فوق التطبيقات الأخرى", "अन्य ऐप्स के ऊपर दिखाएं", "Diğer uygulamaların üzerinde göster", "Wyświetlaj nad innymi aplikacjami", "Tampilkan di atas aplikasi lain", "Показувати поверх інших програм", "Hiển thị trên ứng dụng khác"
        ),
        "permission_draw_over_other_apps_explanation" to t(
            "Laat ondersteunde popupvensters boven andere apps verschijnen. Zonder dit gebruikt de app normale meldingen.",
            "Allows supported popup windows to appear over other apps. Without it, the app uses normal notifications.",
            "Permite que ventanas emergentes compatibles aparezcan sobre otras apps. Sin esto, la app usa notificaciones normales.",
            "Permite que popups suportados apareçam sobre outros apps. Sem isso, o app usa notificações normais.",
            "Erlaubt unterstützten Popupfenstern, über anderen Apps zu erscheinen. Ohne dies nutzt die App normale Benachrichtigungen.",
            "Permet aux fenêtres popup prises en charge d'apparaître au-dessus des autres apps. Sinon l'app utilise les notifications normales.",
            "Consente ai popup supportati di apparire sopra altre app. Senza, l'app usa notifiche normali.",
            "지원되는 팝업 창이 다른 앱 위에 표시됩니다. 없으면 일반 알림을 사용합니다.",
            "允许支持的弹窗显示在其他应用之上。否则应用使用普通通知。",
            "対応するポップアップを他のアプリの上に表示できます。無効時は通常通知を使います。",
            "Позволяет поддерживаемым всплывающим окнам появляться поверх других приложений. Без этого используются обычные уведомления.",
            "يسمح للنوافذ المنبثقة المدعومة بالظهور فوق التطبيقات الأخرى. بدونه يستخدم التطبيق الإشعارات العادية.",
            "समर्थित पॉपअप विंडो को अन्य ऐप्स के ऊपर दिखने देता है। इसके बिना ऐप सामान्य सूचनाएं उपयोग करता है।",
            "Desteklenen açılır pencerelerin diğer uygulamaların üzerinde görünmesini sağlar. Yoksa normal bildirimler kullanılır.",
            "Pozwala obsługiwanym oknom popup pojawiać się nad innymi aplikacjami. Bez tego aplikacja używa zwykłych powiadomień.",
            "Memungkinkan popup yang didukung muncul di atas aplikasi lain. Tanpanya aplikasi memakai notifikasi normal.",
            "Дозволяє підтримуваним popup-вікнам з'являтися поверх інших програм. Без цього використовуються звичайні сповіщення.",
            "Cho phép cửa sổ bật lên được hỗ trợ hiển thị trên ứng dụng khác. Nếu không, ứng dụng dùng thông báo bình thường."
        ),
        "permission_status_granted" to t(
            "Toegestaan", "Granted", "Concedido", "Concedido", "Erlaubt", "Accordé", "Concesso", "허용됨", "已授予", "許可済み",
            "Разрешено", "مسموح", "अनुमति दी गई", "Verildi", "Przyznano", "Diizinkan", "Надано", "Đã cấp"
        ),
        "permission_status_optional_missing" to t(
            "Optioneel, nog niet gegeven", "Optional, not yet granted", "Opcional, aún no concedido", "Opcional, ainda não concedida", "Optional, noch nicht erteilt", "Facultatif, pas encore accordé", "Facoltativo, non ancora concesso", "선택 사항, 아직 미허용", "可选，尚未授予", "任意、未許可",
            "Необязательно, ещё не выдано", "اختياري، لم يُمنح بعد", "वैकल्पिक, अभी नहीं दिया", "İsteğe bağlı, henüz verilmedi", "Opcjonalne, jeszcze nie przyznane", "Opsional, belum diberikan", "Необов’язково, ще не надано", "Tùy chọn, chưa được cấp"
        ),
        "permission_status_required_missing" to t(
            "Vereist", "Required", "Obligatorio", "Obrigatório", "Erforderlich", "Obligatoire", "Obbligatorio", "필수", "必需", "必須",
            "Обязательно", "مطلوب", "आवश्यक", "Gerekli", "Wymagane", "Wajib", "Обов’язково", "Bắt buộc"
        ),
        "permission_hint_skip_calendar" to t(
            "Je kunt dit overslaan en later toestaan via KalenderAlarm-instellingen.",
            "You can skip this and allow it later in Calendar Alarm settings.",
            "Puedes omitirlo y permitirlo más tarde en los ajustes de Calendar Alarm.",
            "Você pode pular e permitir depois nas configurações do Calendar Alarm.",
            "Du kannst dies überspringen und später in den Kalender-Wecker-Einstellungen erlauben.",
            "Vous pouvez ignorer et autoriser plus tard dans les paramètres Calendar Alarm.",
            "Puoi saltare e consentire più tardi nelle impostazioni di Calendar Alarm.",
            "나중에 캘린더 알람 설정에서 허용할 수 있으므로 건너뛸 수 있습니다.",
            "您可以跳过此步骤，稍后在日历闹钟设置中允许。",
            "スキップして、後からカレンダーアラーム設定で許可できます。",
            "Можно пропустить и разрешить позже в настройках Calendar Alarm.",
            "يمكنك التخطي والسماح لاحقًا من إعدادات Calendar Alarm.",
            "आप इसे छोड़ सकते हैं और बाद में कैलेंडर अलार्म सेटिंग्स में अनुमति दे सकते हैं।",
            "Bunu atlayıp daha sonra Takvim Alarmı ayarlarından izin verebilirsiniz.",
            "Możesz pominąć i przyznać później w ustawieniach Calendar Alarm.",
            "Anda bisa melewati dan mengizinkan nanti di pengaturan Calendar Alarm.",
            "Можна пропустити й дозволити пізніше в налаштуваннях Calendar Alarm.",
            "Bạn có thể bỏ qua và cho phép sau trong cài đặt Calendar Alarm."
        ),
        "permission_allow" to t(
            "Toestaan", "Allow", "Permitir", "Permitir", "Erlauben", "Autoriser", "Consenti", "허용", "允许", "許可",
            "Разрешить", "السماح", "अनुमति दें", "İzin ver", "Zezwól", "Izinkan", "Дозволити", "Cho phép"
        ),
        "permission_grant" to t(
            "Verleen", "Grant", "Conceder", "Conceder", "Erteilen", "Accorder", "Concedi", "부여", "授予", "付与",
            "Выдать", "منح", "दें", "Ver", "Przyznaj", "Berikan", "Надати", "Cấp"
        ),
        "permission_info" to t(
            "Informatie", "Information", "Información", "Informação", "Information", "Information", "Informazioni", "정보", "信息", "情報",
            "Информация", "معلومات", "जानकारी", "Bilgi", "Informacje", "Informasi", "Інформація", "Thông tin"
        ),
        "permission_button_check_permissions" to t(
            "Toestemmingen controleren", "Check permissions", "Comprobar permisos", "Verificar permissões", "Berechtigungen prüfen", "Vérifier les autorisations", "Controlla autorizzazioni", "권한 확인", "检查权限", "権限を確認",
            "Проверить разрешения", "التحقق من الأذونات", "अनुमतियाँ जाँचें", "İzinleri kontrol et", "Sprawdź uprawnienia", "Periksa izin", "Перевірити дозволи", "Kiểm tra quyền"
        ),
        "permission_button_start_app" to t(
            "Start app", "Start app", "Iniciar app", "Iniciar app", "App starten", "Lancer l’app", "Avvia app", "앱 시작", "启动应用", "アプリを開始",
            "Запустить приложение", "بدء التطبيق", "ऐप शुरू करें", "Uygulamayı başlat", "Uruchom aplikację", "Mulai aplikasi", "Запустити застосунок", "Mở ứng dụng"
        ),
        "permission_button_recheck" to t(
            "Ik heb de toestemmingen gegeven, controleer opnieuw", "I granted the permissions, check again", "Ya concedí los permisos, comprobar de nuevo", "Já concedi as permissões, verificar novamente", "Ich habe die Berechtigungen erteilt, erneut prüfen", "J’ai accordé les autorisations, vérifier à nouveau", "Ho concesso le autorizzazioni, ricontrolla", "권한을 허용했습니다. 다시 확인", "我已授予权限，请重新检查", "許可しました。再確認",
            "Я выдал разрешения, проверить снова", "لقد منحت الأذونات، أعد التحقق", "मैंने अनुमतियाँ दे दीं, फिर जाँचें", "İzinleri verdim, yeniden kontrol et", "Przyznano uprawnienia, sprawdź ponownie", "Saya sudah memberi izin, periksa lagi", "Я надав дозволи, перевірити знову", "Tôi đã cấp quyền, kiểm tra lại"
        ),
        "cd_warning" to t(
            "Waarschuwing", "Warning", "Advertencia", "Aviso", "Warnung", "Avertissement", "Avviso", "경고", "警告", "警告",
            "Предупреждение", "تحذير", "चेतावनी", "Uyarı", "Ostrzeżenie", "Peringatan", "Попередження", "Cảnh báo"
        ),

        // App-statusbanner (KalenderAlarm-hub)
        "app_status_ok" to t(
            "✓ App draait normaal", "✓ App is running normally", "✓ La app funciona con normalidad", "✓ O app está funcionando normalmente", "✓ App läuft normal", "✓ L’application fonctionne normalement", "✓ L’app funziona normalmente", "✓ 앱이 정상 작동 중", "✓ 应用运行正常", "✓ アプリは正常に動作しています",
            "✓ Приложение работает нормально", "✓ التطبيق يعمل بشكل طبيعي", "✓ ऐप सामान्य रूप से चल रहा है", "✓ Uygulama normal çalışıyor", "✓ Aplikacja działa normalnie", "✓ Aplikasi berjalan normal", "✓ Застосунок працює нормально", "✓ Ứng dụng đang chạy bình thường"
        ),
        "app_status_not_active" to t(
            "⚠️ App niet actief in achtergrond", "⚠️ App not active in background", "⚠️ App no activa en segundo plano", "⚠️ App inativa em segundo plano", "⚠️ App im Hintergrund nicht aktiv", "⚠️ Application inactive en arrière-plan", "⚠️ App non attiva in background", "⚠️ 백그라운드에서 앱이 비활성", "⚠️ 应用未在后台运行", "⚠️ アプリがバックグラウンドで無効",
            "⚠️ Приложение не активно в фоне", "⚠️ التطبيق غير نشط في الخلفية", "⚠️ ऐप पृष्ठभूमि में सक्रिय नहीं", "⚠️ Uygulama arka planda aktif değil", "⚠️ Aplikacja nieaktywna w tle", "⚠️ Aplikasi tidak aktif di latar", "⚠️ Застосунок неактивний у фоні", "⚠️ Ứng dụng không hoạt động nền"
        ),
        "app_status_services_disabled" to t(
            "⚠️ Kritieke services zijn uitgeschakeld", "⚠️ Critical services are disabled", "⚠️ Servicios críticos desactivados", "⚠️ Serviços críticos desativados", "⚠️ Kritische Dienste sind deaktiviert", "⚠️ Services critiques désactivés", "⚠️ Servizi critici disattivati", "⚠️ 중요 서비스가 꺼져 있음", "⚠️ 关键服务已关闭", "⚠️ 重要なサービスが無効です",
            "⚠️ Критические службы отключены", "⚠️ الخدمات الحرجة معطلة", "⚠️ महत्वपूर्ण सेवाएँ बंद हैं", "⚠️ Kritik servisler devre dışı", "⚠️ Krytyczne usługi wyłączone", "⚠️ Layanan penting dinonaktifkan", "⚠️ Критичні служби вимкнено", "⚠️ Dịch vụ quan trọng đã tắt"
        ),
        "app_status_not_active_and_services" to t(
            "⚠️ App niet actief en services uitgeschakeld", "⚠️ App not active and services disabled", "⚠️ App inactiva y servicios desactivados", "⚠️ App inativa e serviços desativados", "⚠️ App nicht aktiv und Dienste deaktiviert", "⚠️ Application inactive et services désactivés", "⚠️ App non attiva e servizi disattivati", "⚠️ 앱 비활성 및 서비스 꺼짐", "⚠️ 应用未运行且服务已关闭", "⚠️ アプリ非アクティブでサービス無効",
            "⚠️ Приложение не активно и службы отключены", "⚠️ التطبيق غير نشط والخدمات معطلة", "⚠️ ऐप सक्रिय नहीं और सेवाएँ बंद", "⚠️ Uygulama aktif değil ve servisler kapalı", "⚠️ Aplikacja nieaktywna i usługi wyłączone", "⚠️ Aplikasi tidak aktif dan layanan dimatikan", "⚠️ Застосунок неактивний і служби вимкнено", "⚠️ Ứng dụng không hoạt động và dịch vụ đã tắt"
        ),
        "app_status_periodic_sync_warning" to t(
            "Periodieke sync werkt mogelijk niet correct", "Periodic sync may not work correctly", "La sincronización periódica podría no funcionar bien", "A sincronização periódica pode não funcionar corretamente", "Periodische Synchronisation funktioniert möglicherweise nicht richtig", "La synchronisation périodique peut ne pas fonctionner correctement", "La sincronizzazione periodica potrebbe non funzionare correttamente", "주기적 동기화가 제대로 작동하지 않을 수 있음", "定期同步可能无法正常工作", "定期同期が正しく動作しない場合があります",
            "Периодическая синхронизация может работать некорректно", "قد لا تعمل المزامنة الدورية بشكل صحيح", "आवधिक सिंक सही से काम न करे", "Periyodik senkronizasyon düzgün çalışmayabilir", "Synchronizacja okresowa może działać nieprawidłowo", "Sinkronisasi berkala mungkin tidak berfungsi dengan benar", "Періодична синхронізація може працювати некоректно", "Đồng bộ định kỳ có thể không hoạt động đúng"
        ),
        
        // Home Assistant Settings
        "ha_paste" to t(
            "Plakken", "Paste", "Pegar", "Colar", "Einfügen", "Coller", "Incolla", "붙여넣기", "粘贴", "貼り付け",
            "Вставить", "لصق", "चिपकाएं", "Yapıştır", "Wklej", "Tempel", "Вставити", "Dán"
        ),
        "ha_paste_description" to t(
            "Plak gestructureerde tekst met URL:, Token: en Entities: secties.", "Paste structured text with URL:, Token: and Entities: sections.", "Pegue texto estructurado con secciones URL:, Token: y Entities:.", "Cole texto estruturado com seções URL:, Token: e Entities:.", "Fügen Sie strukturierten Text mit URL:, Token: und Entities: Abschnitten ein.", "Collez du texte structuré avec les sections URL:, Token: et Entities:.", "Incolla testo strutturato con sezioni URL:, Token: ed Entities:.", "URL:, Token: 및 Entities: 섹션이 있는 구조화된 텍스트를 붙여넣으세요.", "粘贴包含 URL:、Token: 和 Entities: 部分的结构化文本。", "URL:、Token:、Entities: セクションを含む構造化テキストを貼り付けます。",
            "Вставьте структурированный текст с разделами URL:, Token: и Entities:.", "الصق نصًا منظمًا يحتوي على أقسام URL: و Token: و Entities:.", "URL:, Token: और Entities: अनुभागों के साथ संरचित पाठ चिपकाएं।", "URL:, Token: ve Entities: bölümleri içeren yapılandırılmış metin yapıştırın.", "Wklej ustrukturyzowany tekst z sekcjami URL:, Token: i Entities:.", "Tempel teks terstruktur dengan bagian URL:, Token: dan Entities:.", "Вставте структурований текст з розділами URL:, Token: та Entities:.", "Dán văn bản có cấu trúc với các phần URL:, Token: và Entities:."
        ),
        "ha_paste_hint" to t(
            "Plak hier je configuratie...", "Paste your configuration here...", "Pegue su configuración aquí...", "Cole sua configuração aqui...", "Fügen Sie hier Ihre Konfiguration ein...", "Collez votre configuration ici...", "Incolla qui la tua configurazione...", "여기에 구성을 붙여넣으세요...", "在此粘贴您的配置...", "ここに設定を貼り付けてください...",
            "Вставьте конфигурацию здесь...", "الصق التكوين الخاص بك هنا...", "अपना कॉन्फ़िगरेशन यहां चिपकाएं...", "Yapılandırmanızı buraya yapıştırın...", "Wklej tutaj swoją konfigurację...", "Tempel konfigurasi Anda di sini...", "Вставте конфігурацію тут...", "Dán cấu hình của bạn ở đây..."
        ),
        "ha_paste_import" to t(
            "Importeren", "Import", "Importar", "Importar", "Importieren", "Importer", "Importa", "가져오기", "导入", "インポート",
            "Импорт", "استيراد", "आयात करें", "İçe Aktar", "Importuj", "Impor", "Імпорт", "Nhập"
        ),
        "ha_paste_clipboard" to t(
            "Plakken", "Paste", "Pegar", "Colar", "Einfügen", "Coller", "Incolla", "붙여넣기", "粘贴", "貼り付け",
            "Вставить", "لصق", "चिपकाएं", "Yapıştır", "Wklej", "Tempel", "Вставити", "Dán"
        ),
        "ha_export" to t(
            "Exporteren", "Export", "Exportar", "Exportar", "Exportieren", "Exporter", "Esporta", "내보내기", "导出", "エクスポート",
            "Экспорт", "تصدير", "निर्यात करें", "Dışa Aktar", "Eksportuj", "Ekspor", "Експорт", "Xuất"
        ),
        "ha_urls" to t(
            "Home Assistant URLs", "Home Assistant URLs", "URLs de Home Assistant", "URLs do Home Assistant", "Home Assistant URLs", "URLs Home Assistant", "URL Home Assistant", "Home Assistant URL", "Home Assistant URL", "Home Assistant URL",
            "URL Home Assistant", "عناوين Home Assistant", "Home Assistant URL", "Home Assistant URL'leri", "Adresy URL Home Assistant", "URL Home Assistant", "URL Home Assistant", "URL Home Assistant"
        ),
        "ha_token" to t(
            "Long-Lived Token", "Long-Lived Token", "Token de larga duración", "Token de longa duração", "Langlebiger Token", "Jeton longue durée", "Token a lunga durata", "장기 토큰", "长期令牌", "長期トークン",
            "Долгосрочный токен", "رمز طويل الأمد", "दीर्घकालिक टोकन", "Uzun Ömürlü Token", "Token długoterminowy", "Token Jangka Panjang", "Довгостроковий токен", "Token lâu dài"
        ),
        "ha_entities" to t(
            "Entiteiten", "Entities", "Entidades", "Entidades", "Entitäten", "Entités", "Entità", "엔티티", "实体", "エンティティ",
            "Сущности", "الكيانات", "इकाइयाँ", "Varlıklar", "Encje", "Entitas", "Сутності", "Thực thể"
        ),
        "ha_add_entity" to t(
            "Entiteit toevoegen", "Add entity", "Agregar entidad", "Adicionar entidade", "Entität hinzufügen", "Ajouter entité", "Aggiungi entità", "엔티티 추가", "添加实体", "エンティティを追加",
            "Добавить сущность", "إضافة كيان", "इकाई जोड़ें", "Varlık ekle", "Dodaj encję", "Tambah entitas", "Додати сутність", "Thêm thực thể"
        ),
        "ha_add_entity_desc" to t(
            "Voer entiteit ID's in (één per regel)", "Enter entity IDs (one per line)", "Ingrese IDs de entidad (uno por línea)", "Digite IDs de entidade (um por linha)", "Entitäts-IDs eingeben (eine pro Zeile)", "Entrez les IDs d'entité (un par ligne)", "Inserisci gli ID entità (uno per riga)", "엔티티 ID 입력 (한 줄에 하나)", "输入实体 ID（每行一个）", "エンティティIDを入力（1行に1つ）",
            "Введите ID сущностей (по одному в строке)", "أدخل معرفات الكيان (واحد لكل سطر)", "इकाई ID दर्ज करें (प्रति पंक्ति एक)", "Varlık ID'lerini girin (satır başına bir)", "Wprowadź ID encji (jeden na linię)", "Masukkan ID entitas (satu per baris)", "Введіть ID сутностей (по одному в рядку)", "Nhập ID thực thể (mỗi dòng một)"
        ),
        "ha_entity_id" to t(
            "Entiteit ID", "Entity ID", "ID de entidad", "ID da entidade", "Entitäts-ID", "ID d'entité", "ID entità", "엔티티 ID", "实体 ID", "エンティティID",
            "ID сущности", "معرف الكيان", "इकाई ID", "Varlık ID", "ID encji", "ID entitas", "ID сутності", "ID thực thể"
        ),
        "ha_add" to t(
            "Toevoegen", "Add", "Agregar", "Adicionar", "Hinzufügen", "Ajouter", "Aggiungi", "추가", "添加", "追加",
            "Добавить", "إضافة", "जोड़ें", "Ekle", "Dodaj", "Tambah", "Додати", "Thêm"
        ),
        "ha_speaker" to t(
            "Externe Speaker", "External Speaker", "Altavoz Externo", "Alto-falante Externo", "Externer Lautsprecher", "Haut-parleur externe", "Altoparlante esterno", "외부 스피커", "外部扬声器", "外部スピーカー",
            "Внешний динамик", "مكبر صوت خارجي", "बाहरी स्पीकर", "Harici Hoparlör", "Głośnik zewnętrzny", "Speaker Eksternal", "Зовнішній динамік", "Loa ngoài"
        ),
        "ha_presence" to t(
            "Thuis detectie", "Home detection", "Detección de presencia", "Detecção de presença", "Anwesenheitserkennung", "Détection de présence", "Rilevamento presenza", "재택 감지", "在家检测", "在宅検知",
            "Обнаружение присутствия", "كشف التواجد", "उपस्थिति का पता लगाना", "Ev tespiti", "Wykrywanie obecności", "Deteksi kehadiran", "Виявлення присутності", "Phát hiện có nhà"
        ),
        "ha_out_of_bed" to t(
            "Uit bed check", "Out of bed check", "Verificación fuera de cama", "Verificação fora da cama", "Aus-dem-Bett-Prüfung", "Vérif. hors du lit", "Controllo fuori dal letto", "침대 밖 확인", "离床检查", "ベッド外チェック",
            "Проверка вне кровати", "فحص خارج السرير", "बिस्तर से बाहर जाँच", "Yataktan çıkma kontrolü", "Sprawdzenie poza łóżkiem", "Cek keluar dari tempat tidur", "Перевірка поза ліжком", "Kiểm tra ra khỏi giường"
        ),
        "ha_out_of_bed_requires_presence_short" to t(
            "Vereist dat Thuis detectie is ingeschakeld.", "Requires Home detection to be enabled.", "Requiere que la detección de presencia esté activada.", "Requer que a detecção de presença esteja ativada.", "Erfordert aktivierte Anwesenheitserkennung.", "Nécessite que la détection de présence soit activée.", "Richiede il rilevamento presenza attivo.", "재택 감지가 켜져 있어야 합니다.", "需要启用在家检测。", "在宅検知を有効にする必要があります。",
            "Требуется включить обнаружение присутствия.", "يتطلب تفعيل اكتشاف التواجد.", "इसके लिए उपस्थिति पहचान चालू होनी चाहिए।", "Ev tespitinin açık olması gerekir.", "Wymaga włączonego wykrywania obecności.", "Memerlukan deteksi kehadiran aktif.", "Потрібно увімкнути виявлення присутності.", "Yêu cầu bật phát hiện có nhà."
        ),
        "ha_out_of_bed_requires_presence" to t(
            "Uit bed check vereist Thuis detectie. Schakel eerst Thuis detectie in.", "Out of bed check requires Home detection. Enable Home detection first.", "La verificación fuera de cama requiere detección de presencia. Actívala primero.", "A verificação fora da cama requer detecção de presença. Ative-a primeiro.", "Die Aus-dem-Bett-Prüfung erfordert Anwesenheitserkennung. Aktiviere sie zuerst.", "La vérification hors du lit nécessite la détection de présence. Activez-la d'abord.", "Il controllo fuori dal letto richiede il rilevamento presenza. Attivalo prima.", "침대 밖 확인에는 재택 감지가 필요합니다. 먼저 재택 감지를 켜세요.", "离床检查需要在家检测。请先启用在家检测。", "ベッド外チェックには在宅検知が必要です。先に在宅検知を有効にしてください。",
            "Проверка вне кровати требует обнаружения присутствия. Сначала включите его.", "يتطلب فحص خارج السرير اكتشاف التواجد. فعّله أولاً.", "बिस्तर से बाहर जाँच के लिए उपस्थिति पहचान आवश्यक है। पहले इसे चालू करें।", "Yataktan çıkma kontrolü için ev tespiti gerekir. Önce ev tespitini etkinleştirin.", "Sprawdzenie poza łóżkiem wymaga wykrywania obecności. Najpierw je włącz.", "Cek keluar dari tempat tidur memerlukan deteksi kehadiran. Aktifkan terlebih dahulu.", "Перевірка поза ліжком потребує виявлення присутності. Спочатку увімкніть його.", "Kiểm tra ra khỏi giường yêu cầu phát hiện có nhà. Hãy bật tính năng này trước."
        ),
        "ha_enable_presence_detection" to t(
            "Thuis detectie inschakelen", "Enable Home detection", "Activar detección de presencia", "Ativar detecção de presença", "Anwesenheitserkennung aktivieren", "Activer la détection de présence", "Attiva rilevamento presenza", "재택 감지 켜기", "启用在家检测", "在宅検知を有効化",
            "Включить обнаружение присутствия", "تفعيل اكتشاف التواجد", "उपस्थिति पहचान चालू करें", "Ev tespitini etkinleştir", "Włącz wykrywanie obecności", "Aktifkan deteksi kehadiran", "Увімкнути виявлення присутності", "Bật phát hiện có nhà"
        ),
        "ha_out_of_bed_unavailable_without_presence" to t(
            "Niet beschikbaar zolang Thuis detectie uit staat.", "Unavailable while Home detection is off.", "No disponible mientras la detección de presencia esté desactivada.", "Indisponível enquanto a detecção de presença estiver desativada.", "Nicht verfügbar, solange die Anwesenheitserkennung ausgeschaltet ist.", "Indisponible tant que la détection de présence est désactivée.", "Non disponibile finché il rilevamento presenza è disattivato.", "재택 감지가 꺼져 있으면 사용할 수 없습니다.", "在家检测关闭时不可用。", "在宅検知がオフの間は利用できません。",
            "Недоступно, пока обнаружение присутствия выключено.", "غير متاح ما دام اكتشاف التواجد متوقفاً.", "उपस्थिति पहचान बंद रहने पर उपलब्ध नहीं है।", "Ev tespiti kapalıyken kullanılamaz.", "Niedostępne, gdy wykrywanie obecności jest wyłączone.", "Tidak tersedia saat deteksi kehadiran nonaktif.", "Недоступно, доки виявлення присутності вимкнене.", "Không khả dụng khi phát hiện có nhà đang tắt."
        ),
        "ha_presence_enable" to t(
            "Thuis check inschakelen", "Enable home check", "Activar verificación de presencia", "Ativar verificação de presença", "Anwesenheitsprüfung aktivieren", "Activer vérif. présence", "Attiva controllo presenza", "재택 확인 활성화", "启用在家检查", "在宅確認を有効化",
            "Включить проверку присутствия", "تفعيل فحص التواجد", "उपस्थिति जाँच सक्षम करें", "Ev kontrolünü etkinleştir", "Włącz sprawdzanie obecności", "Aktifkan cek kehadiran", "Увімкнути перевірку присутності", "Bật kiểm tra có nhà"
        ),
        "ha_select_presence_entity" to t(
            "Selecteer aanwezigheidsentiteit", "Select presence entity", "Seleccionar entidad de presencia", "Selecionar entidade de presença", "Anwesenheitsentität wählen", "Sélectionner entité présence", "Seleziona entità presenza", "재택 엔티티 선택", "选择在家实体", "在宅エンティティを選択",
            "Выбрать сущность присутствия", "حدد كيان التواجد", "उपस्थिति इकाई चुनें", "Varlık seçin", "Wybierz encję obecności", "Pilih entitas kehadiran", "Вибрати сутність присутності", "Chọn thực thể có nhà"
        ),
        "ha_script_alarm" to t(
            "Script bij alarm", "Script on alarm", "Script al sonar alarma", "Script no alarme", "Skript bei Alarm", "Script à l'alarme", "Script all'allarme", "알람 시 스크립트", "闹钟触发脚本", "アラーム時スクリプト",
            "Скрипт при будильнике", "سكربت عند المنبه", "अलार्म पर स्क्रिप्ट", "Alarmda script", "Skrypt przy alarmie", "Skrip saat alarm", "Скрипт при будильнику", "Script khi báo thức"
        ),
        "ha_script_alarm_desc" to t(
            "Start een Home Assistant script als het alarm afgaat.", "Run a Home Assistant script when the alarm fires.", "Ejecuta un script de Home Assistant cuando suene la alarma.", "Executa um script do Home Assistant quando o alarme tocar.", "Startet ein Home-Assistant-Skript, wenn der Alarm losgeht.", "Lance un script Home Assistant quand l'alarme sonne.", "Avvia uno script Home Assistant quando suona l'allarme.", "알람이 울리면 Home Assistant 스크립트를 실행합니다.", "闹钟响起时运行 Home Assistant 脚本。", "アラームが鳴ったときに Home Assistant スクリプトを実行します。",
            "Запускает скрипт Home Assistant при срабатывании будильника.", "يشغّل سكربت Home Assistant عند رنين المنبه.", "अलार्म बजने पर Home Assistant स्क्रिप्ट चलाएं।", "Alarm çaldığında bir Home Assistant scripti çalıştırır.", "Uruchamia skrypt Home Assistant, gdy zadzwoni alarm.", "Menjalankan skrip Home Assistant saat alarm berbunyi.", "Запускає скрипт Home Assistant при спрацюванні будильника.", "Chạy script Home Assistant khi báo thức reo."
        ),
        "ha_script_timer" to t(
            "Script bij timer", "Script on timer", "Script al sonar temporizador", "Script no temporizador", "Skript bei Timer", "Script au minuteur", "Script al timer", "타이머 시 스크립트", "计时器触发脚本", "タイマー時スクリプト",
            "Скрипт при таймере", "سكربت عند المؤقت", "टाइमर पर स्क्रिप्ट", "Zamanlayıcıda script", "Skrypt przy minutniku", "Skrip saat timer", "Скрипт при таймері", "Script khi hẹn giờ"
        ),
        "ha_script_timer_desc" to t(
            "Start een Home Assistant script als de timer afloopt.", "Run a Home Assistant script when the timer finishes.", "Ejecuta un script de Home Assistant cuando termine el temporizador.", "Executa um script do Home Assistant quando o temporizador terminar.", "Startet ein Home-Assistant-Skript, wenn der Timer abläuft.", "Lance un script Home Assistant quand le minuteur se termine.", "Avvia uno script Home Assistant quando il timer finisce.", "타이머가 끝나면 Home Assistant 스크립트를 실행합니다.", "计时器结束时运行 Home Assistant 脚本。", "タイマーが終了したときに Home Assistant スクリプトを実行します。",
            "Запускает скрипт Home Assistant по окончании таймера.", "يشغّل سكربت Home Assistant عند انتهاء المؤقت.", "टाइमर समाप्त होने पर Home Assistant स्क्रिप्ट चलाएं।", "Zamanlayıcı bittiğinde bir Home Assistant scripti çalıştırır.", "Uruchamia skrypt Home Assistant po zakończeniu minutnika.", "Menjalankan skrip Home Assistant saat timer selesai.", "Запускає скрипт Home Assistant по завершенню таймера.", "Chạy script Home Assistant khi hẹn giờ kết thúc."
        ),
        "ha_script_enable" to t(
            "Script inschakelen", "Enable script", "Activar script", "Ativar script", "Skript aktivieren", "Activer le script", "Attiva script", "스크립트 활성화", "启用脚本", "スクリプトを有効化",
            "Включить скрипт", "تفعيل السكربت", "स्क्रिप्ट सक्षम करें", "Scripti etkinleştir", "Włącz skrypt", "Aktifkan skrip", "Увімкнути скрипт", "Bật script"
        ),
        "ha_select_script_entity" to t(
            "Selecteer script", "Select script", "Seleccionar script", "Selecionar script", "Skript wählen", "Sélectionner un script", "Seleziona script", "스크립트 선택", "选择脚本", "スクリプトを選択",
            "Выбрать скрипт", "حدد السكربت", "स्क्रिप्ट चुनें", "Script seçin", "Wybierz skrypt", "Pilih skrip", "Вибрати скрипт", "Chọn script"
        ),
        "ha_script_run_condition" to t(
            "Wanneer uitvoeren?", "When to run?", "¿Cuándo ejecutar?", "Quando executar?", "Wann ausführen?", "Quand exécuter ?", "Quando eseguire?", "실행 조건", "何时运行？", "実行タイミング",
            "Когда запускать?", "متى يتم التشغيل؟", "कब चलाएं?", "Ne zaman çalıştırılsın?", "Kiedy uruchamiać?", "Kapan dijalankan?", "Коли запускати?", "Khi nào chạy?"
        ),
        "ha_script_always_run" to t(
            "Altijd uitvoeren", "Always run", "Ejecutar siempre", "Executar sempre", "Immer ausführen", "Toujours exécuter", "Esegui sempre", "항상 실행", "始终运行", "常に実行",
            "Всегда запускать", "دائماً تشغيل", "हमेशा चलाएं", "Her zaman çalıştır", "Zawsze uruchamiaj", "Selalu jalankan", "Завжди запускати", "Luôn chạy"
        ),
        "ha_script_only_if_home" to t(
            "Alleen als gebruiker thuis is", "Only if user is home", "Solo si el usuario está en casa", "Somente se o usuário estiver em casa", "Nur wenn Nutzer zu Hause ist", "Seulement si l'utilisateur est chez lui", "Solo se l'utente è a casa", "사용자가 집에 있을 때만", "仅当用户在家时", "ユーザーが在宅の場合のみ",
            "Только если пользователь дома", "فقط إذا كان المستخدم في المنزل", "केवल यदि उपयोगकर्ता घर पर है", "Sadece kullanıcı evdeyse", "Tylko gdy użytkownik jest w domu", "Hanya jika pengguna di rumah", "Тільки якщо користувач вдома", "Chỉ khi người dùng ở nhà"
        ),

        // Alarm Triggers
        "trigger_first_activation_cancel_warning" to t(
            "Let op: Als je annuleert wordt deze trigger weer uitgeschakeld",
            "Note: If you cancel, this trigger will be turned off again.",
            "Aviso: si cancelas, este disparador se desactivará de nuevo.",
            "Atenção: se cancelar, este gatilho será desativado novamente.",
            "Hinweis: Wenn du abbrichst, wird dieser Auslöser wieder deaktiviert.",
            "Remarque : si vous annulez, ce déclencheur sera à nouveau désactivé.",
            "Attenzione: se annulli, questo trigger verrà di nuovo disattivato.",
            "안내: 취소하면 이 트리거가 다시 꺼집니다.",
            "提示：若取消，此触发器将再次被关闭。",
            "注意: キャンセルすると、このトリガーは再びオフになります。",
            "Внимание: при отмене этот триггер снова будет отключён.",
            "تنبيه: إذا ألغيت، سيتم إيقاف هذا المشغّل مرة أخرى.",
            "ध्यान दें: रद्द करने पर यह ट्रिगर फिर से बंद हो जाएगा।",
            "Not: İptal ederseniz bu tetikleyici yeniden kapanır.",
            "Uwaga: po anulowaniu ten wyzwalacz zostanie ponownie wyłączony.",
            "Perhatian: Jika membatalkan, pemicu ini akan dimatikan lagi.",
            "Увага: якщо скасувати, цей тригер знову буде вимкнено.",
            "Lưu ý: Nếu hủy, kích hoạt này sẽ bị tắt lại."
        ),
        "trigger_options" to t(
            "Opties", "Options", "Opciones", "Opções", "Optionen", "Options", "Opzioni", "옵션", "选项", "オプション",
            "Опции", "خيارات", "विकल्प", "Seçenekler", "Opcje", "Opsi", "Опції", "Tùy chọn"
        ),
        "trigger_snooze_alarm" to t(
            "Simpel Alarm", "Simple Alarm", "Alarma simple", "Alarme simples", "Einfacher Wecker", "Alarme simple", "Allarme semplice", "간단 알람", "简单闹钟", "シンプルアラーム",
            "Простой будильник", "منبه بسيط", "सरल अलार्म", "Basit Alarm", "Prosty alarm", "Alarm sederhana", "Простий будильник", "Báo thức đơn giản"
        ),
        "trigger_snooze_desc" to t(
            "Alarm kan worden uitgezet of gesluimerd.", "Alarm can be dismissed or snoozed.", "La alarma se puede descartar o posponer.", "O alarme pode ser dispensado ou adiado.", "Alarm kann beendet oder verschoben werden.", "L'alarme peut être ignorée ou reportée.", "L'allarme può essere ignorato o posticipato.", "알람을 해제하거나 다시 알릴 수 있습니다.", "可以关闭或贪睡闹钟。", "アラームを閉じるかスヌーズできます。",
            "Будильник можно отклонить или отложить.", "يمكن تجاهل المنبه أو تأجيله.", "अलार्म को खारिज या स्नूज़ किया जा सकता है।", "Alarm kapatılabilir veya ertelenebilir.", "Alarm można odrzucić lub odłożyć.", "Alarm dapat ditutup atau ditunda.", "Будильник можна відхилити або відкласти.", "Có thể tắt hoặc báo lại báo thức."
        ),
        "trigger_smart_alarm" to t(
            "Slim Alarm", "Smart Alarm", "Alarma Inteligente", "Alarme Inteligente", "Intelligenter Alarm", "Alarme intelligent", "Allarme intelligente", "스마트 알람", "智能闹钟", "スマートアラーム",
            "Умный будильник", "منبه ذكي", "स्मार्ट अलार्म", "Akıllı Alarm", "Inteligentny alarm", "Alarm Pintar", "Розумний будильник", "Báo thức thông minh"
        ),
        "trigger_smart_desc" to t(
            "Gebruik Home Assist om een slim alarm te maken, bepaal zelf onder welke voorwaarde, er een alarm moet af gaan, bijvoorbeeld als je thuis bent gebruik een slimme speaker, ben je niet thuis gebruik dan mobiel speaker als alarm, en nog meer.",
            "Use Home Assist to create a smart alarm, determine yourself under which condition an alarm should go off, for example if you are at home use a smart speaker, if you are not at home use mobile speaker as alarm, and more.",
            "Use Home Assist para crear una alarma inteligente, determine usted mismo bajo qué condición debe sonar una alarma, por ejemplo, si está en casa use un altavoz inteligente, si no está en casa use el altavoz móvil como alarma, y más.",
            "Use Home Assist para criar um alarme inteligente, determine você mesmo sob qual condição um alarme deve disparar, por exemplo, se você estiver em casa use um alto-falante inteligente, se não estiver em casa use o alto-falante do celular como alarme, e mais.",
            "Verwenden Sie Home Assist, um einen intelligenten Alarm zu erstellen. Bestimmen Sie selbst, unter welcher Bedingung ein Alarm ausgelöst werden soll, z. B. wenn Sie zu Hause sind, verwenden Sie einen Smart Speaker, wenn Sie nicht zu Hause sind, verwenden Sie den mobilen Lautsprecher als Alarm und mehr.",
            "Utilisez Home Assist pour créer une alarme intelligente, déterminez vous-même dans quelle condition une alarme doit se déclencher, par exemple si vous êtes à la maison utilisez un haut-parleur intelligent, si vous n'êtes pas à la maison utilisez le haut-parleur mobile comme alarme, et plus encore.",
            "Usa Home Assist per creare un allarme intelligente, determina tu stesso in quale condizione deve suonare un allarme, ad esempio se sei a casa usa uno smart speaker, se non sei a casa usa l'altoparlante del cellulare come allarme, e altro ancora.",
            "Home Assist를 사용하여 스마트 알람을 만들고, 알람이 울려야 하는 조건을 직접 결정하세요. 예를 들어 집에 있으면 스마트 스피커를 사용하고, 집에 없으면 휴대폰 스피커를 알람으로 사용하는 등의 작업을 수행할 수 있습니다.",
            "使用 Home Assist 创建智能闹钟，自己确定闹钟应在什么条件下响起，例如，如果您在家，请使用智能扬声器，如果您不在家，请使用手机扬声器作为闹钟等。",
            "Home Assistを使用してスマートアラームを作成し、アラームが鳴る条件を自分で決定します。たとえば、自宅にいる場合はスマートスピーカーを使用し、自宅にいない場合はモバイルスピーカーをアラームとして使用するなど。",
            "Используйте Home Assist для создания умного будильника, определите сами, при каком условии должен сработать будильник, например, если вы дома, используйте умную колонку, если вы не дома, используйте мобильную колонку в качестве будильника и многое другое.",
            "استخدم Home Assist لإنشاء منبه ذكي، حدد بنفسك الشرط الذي يجب أن يرن فيه المنبه، على سبيل المثال إذا كنت في المنزل استخدم مكبر صوت ذكي، إذا لم تكن في المنزل استخدم مكبر صوت الهاتف المحمول كمنبه، والمزيد.",
            "स्मार्ट अलार्म बनाने के लिए Home Assist का उपयोग करें, स्वयं निर्धारित करें कि किस शर्त के तहत अलार्म बजना चाहिए, उदाहरण के लिए यदि आप घर पर हैं तो स्मार्ट स्पीकर का उपयोग करें, यदि आप घर पर नहीं हैं तो मोबाइल स्पीकर को अलार्म के रूप में उपयोग करें, और भी बहुत कुछ।",
            "Akıllı bir alarm oluşturmak için Home Assist'i kullanın, alarmın hangi koşulda çalması gerektiğini kendiniz belirleyin, örneğin evdeyseniz akıllı hoparlör kullanın, evde değilseniz mobil hoparlörü alarm olarak kullanın ve daha fazlası.",
            "Użyj Home Assist, aby stworzyć inteligentny alarm, sam określ, w jakim warunku alarm powinien się włączyć, na przykład jeśli jesteś w domu, użyj inteligentnego głośnika, jeśli nie jesteś w domu, użyj głośnika telefonu jako alarmu i więcej.",
            "Gunakan Home Assist untuk membuat alarm pintar, tentukan sendiri kondisi apa alarm harus berbunyi, misalnya jika Anda di rumah gunakan speaker pintar, jika Anda tidak di rumah gunakan speaker ponsel sebagai alarm, dan banyak lagi.",
            "Використовуйте Home Assist для створення розумного будильника, визначте самі, за якої умови має спрацювати будильник, наприклад, якщо ви вдома, використовуйте розумну колонку, якщо ви не вдома, використовуйте мобільну колонку як будильник тощо.",
            "Sử dụng Home Assist để tạo báo thức thông minh, tự xác định điều kiện báo thức sẽ kêu, ví dụ nếu bạn ở nhà hãy sử dụng loa thông minh, nếu bạn không ở nhà hãy sử dụng loa điện thoại làm báo thức, và nhiều hơn nữa."
        ),
        "trigger_one_time" to t(
            "Alarm gaat maar 1x af", "Alarm rings only once", "La alarma suena solo una vez", "O alarme toca apenas uma vez", "Alarm klingelt nur einmal", "L'alarme ne sonne qu'une fois", "L'allarme suona solo una volta", "알람이 한 번만 울립니다", "闹钟只响一次", "アラームは1回だけ鳴ります",
            "Будильник звонит только один раз", "المنبه يرن مرة واحدة فقط", "अलार्म केवल एक बार बजता है", "Alarm sadece bir kez çalar", "Alarm dzwoni tylko raz", "Alarm berbunyi hanya sekali", "Будильник дзвонить лише один раз", "Báo thức chỉ kêu một lần"
        ),
        "trigger_one_time_desc" to t(
            "Alarm gaat af en stopt automatisch na enkele seconden.", "Alarm goes off and stops automatically after a few seconds.", "La alarma suena y se detiene automáticamente después de unos segundos.", "O alarme toca e para automaticamente após alguns segundos.", "Alarm klingelt und stoppt automatisch nach einigen Sekunden.", "L'alarme sonne et s'arrête automatiquement après quelques secondes.", "L'allarme suona e si ferma automaticamente dopo alcuni secondi.", "알람이 울리고 몇 초 후 자동으로 멈춥니다.", "闹钟响起并在几秒钟后自动停止。", "アラームが鳴り、数秒後に自動的に停止します。",
            "Будильник звонит и автоматически останавливается через несколько секунд.", "يرن المنبه ويتوقف تلقائيًا بعد بضع ثوانٍ.", "अलार्म बजता है और कुछ सेकंड के बाद स्वचालित रूप से बंद हो जाता है।", "Alarm çalar ve birkaç saniye sonra otomatik olarak durur.", "Alarm dzwoni i zatrzymuje się automatycznie po kilku sekundach.", "Alarm berbunyi dan berhenti otomatis setelah beberapa detik.", "Будильник дзвонить і автоматично зупиняється через кілька секунд.", "Báo thức kêu và tự động dừng sau vài giây."
        ),
        
        // Home Assist descriptions
        "ha_urls_desc" to t(
            "Voeg Home Assistant URL's toe voor verbinding.", "Add Home Assistant URLs for connection.", "Agregue URL de Home Assistant para la conexión.", "Adicione URLs do Home Assistant para conexão.", "Fügen Sie Home Assistant URLs für die Verbindung hinzu.", "Ajoutez des URL Home Assistant pour la connexion.", "Aggiungi URL Home Assistant per la connessione.", "연결을 위해 Home Assistant URL을 추가하세요.", "添加 Home Assistant URL 以进行连接。", "接続用のHome Assistant URLを追加します。",
            "Добавьте URL Home Assistant для подключения.", "أضف عناوين URL لـ Home Assistant للاتصال.", "कनेक्शन के लिए Home Assistant URL जोड़ें।", "Bağlantı için Home Assistant URL'leri ekleyin.", "Dodaj adresy URL Home Assistant do połączenia.", "Tambahkan URL Home Assistant untuk koneksi.", "Додайте URL Home Assistant для підключення.", "Thêm URL Home Assistant để kết nối."
        ),
        "ha_token_desc" to t(
            "Voer uw Long-Lived Access Token in voor authenticatie.", "Enter your Long-Lived Access Token for authentication.", "Ingrese su Token de acceso de larga duración para autenticación.", "Insira seu Token de acesso de longa duração para autenticação.", "Geben Sie Ihr langlebiges Zugriffstoken zur Authentifizierung ein.", "Entrez votre jeton d'accès longue durée pour l'authentification.", "Inserisci il tuo Token di accesso a lunga durata per l'autenticazione.", "인증을 위해 장기 액세스 토큰을 입력하세요.", "输入您的长期访问令牌进行身份验证。", "認証用の長期アクセストークンを入力します。",
            "Введите свой долгосрочный токен доступа для аутентификации.", "أدخل رمز الوصول طويل الأمد للمصادقة.", "प्रमाणीकरण के लिए अपना दीर्घकालिक एक्सेस टोकन दर्ज करें।", "Kimlik doğrulama için Uzun Ömürlü Erişim Belirtecinizi girin.", "Wprowadź swój długoterminowy token dostępu do uwierzytelnienia.", "Masukkan Token Akses Jangka Panjang Anda untuk autentikasi.", "Введіть свій довгостроковий токен доступу для автентифікації.", "Nhập Token truy cập lâu dài của bạn để xác thực."
        ),
        "ha_token_tip" to t(
            "Tip: Maak een Long-Lived Token aan in Home Assistant onder Profiel > Beveiliging.", "Tip: Create a Long-Lived Token in Home Assistant under Profile > Security.", "Consejo: Cree un Token de larga duración en Home Assistant en Perfil > Seguridad.", "Dica: Crie um Token de longa duração no Home Assistant em Perfil > Segurança.", "Tipp: Erstellen Sie ein langlebiges Token in Home Assistant unter Profil > Sicherheit.", "Astuce: Créez un jeton longue durée dans Home Assistant sous Profil > Sécurité.", "Suggerimento: Crea un Token a lunga durata in Home Assistant in Profilo > Sicurezza.", "팁: 프로필 > 보안에서 Home Assistant에서 장기 토큰을 만드세요.", "提示：在 Home Assistant 的个人资料 > 安全性下创建长期令牌。", "ヒント：プロフィール > セキュリティでHome Assistantで長期トークンを作成します。",
            "Совет: Создайте долгосрочный токен в Home Assistant в разделе Профиль > Безопасность.", "نصيحة: أنشئ رمزًا طويل الأمد في Home Assistant ضمن الملف الشخصي > الأمان.", "सुझाव: प्रोफ़ाइल > सुरक्षा के तहत Home Assistant में दीर्घकालिक टोकन बनाएं।", "İpucu: Profil > Güvenlik altında Home Assistant'ta Uzun Ömürlü Belirteç oluşturun.", "Wskazówka: Utwórz długoterminowy token w Home Assistant w Profil > Bezpieczeństwo.", "Tip: Buat Token Jangka Panjang di Home Assistant di bawah Profil > Keamanan.", "Порада: Створіть довгостроковий токен у Home Assistant у розділі Профіль > Безпека.", "Mẹo: Tạo Token lâu dài trong Home Assistant trong Hồ sơ > Bảo mật."
        ),
        "ha_entities_desc" to t(
            "Beheer entiteiten die gebruikt worden voor automatisering.", "Manage entities used for automation.", "Administre entidades utilizadas para automatización.", "Gerencie entidades usadas para automação.", "Verwalten Sie Entitäten, die für die Automatisierung verwendet werden.", "Gérez les entités utilisées pour l'automatisation.", "Gestisci le entità utilizzate per l'automazione.", "자동화에 사용되는 엔티티를 관리하세요.", "管理用于自动化的实体。", "自動化に使用されるエンティティを管理します。",
            "Управляйте сущностями, используемыми для автоматизации.", "إدارة الكيانات المستخدمة للأتمتة.", "स्वचालन के लिए उपयोग की जाने वाली इकाइयों का प्रबंधन करें।", "Otomasyon için kullanılan varlıkları yönetin.", "Zarządzaj encjami używanymi do automatyzacji.", "Kelola entitas yang digunakan untuk otomasi.", "Керуйте сутностями, що використовуються для автоматизації.", "Quản lý các thực thể được sử dụng cho tự động hóa."
        ),
        "ha_speaker_desc" to t(
            "Configureer externe speaker voor alarm.", "Configure external speaker for alarm.", "Configure altavoz externo para alarma.", "Configure alto-falante externo para alarme.", "Konfigurieren Sie externen Lautsprecher für Alarm.", "Configurez le haut-parleur externe pour l'alarme.", "Configura altoparlante esterno per allarme.", "알람용 외부 스피커를 구성하세요.", "配置外部扬声器用于闹钟。", "アラーム用の外部スピーカーを構成します。",
            "Настройте внешний динамик для будильника.", "قم بتكوين مكبر الصوت الخارجي للمنبه.", "अलार्म के लिए बाहरी स्पीकर कॉन्फ़िगर करें।", "Alarm için harici hoparlörü yapılandırın.", "Skonfiguruj zewnętrzny głośnik dla alarmu.", "Konfigurasi speaker eksternal untuk alarm.", "Налаштуйте зовнішній динамік для будильника.", "Cấu hình loa ngoài cho báo thức."
        ),
        "ha_settings_title" to t(
            "Home Assistant Instellingen", "Home Assistant Settings", "Configuración de Home Assistant", "Configurações do Home Assistant", "Home Assistant Einstellungen", "Paramètres Home Assistant", "Impostazioni Home Assistant", "Home Assistant 설정", "Home Assistant 设置", "Home Assistant 設定",
            "Настройки Home Assistant", "إعدادات Home Assistant", "Home Assistant सेटिंग्स", "Home Assistant Ayarları", "Ustawienia Home Assistant", "Pengaturan Home Assistant", "Налаштування Home Assistant", "Cài đặt Home Assistant"
        ),
        "ha_urls_configured" to t(
            "URL(s) geconfigureerd", "URL(s) configured", "URL(s) configuradas", "URL(s) configuradas", "URL(s) konfiguriert", "URL(s) configurées", "URL configurati", "URL 구성됨", "已配置URL", "URL設定済み",
            "URL настроены", "تم تكوين URL", "URL कॉन्फ़िगर किया गया", "URL yapılandırıldı", "URL skonfigurowane", "URL dikonfigurasi", "URL налаштовано", "Đã cấu hình URL"
        ),
        "ha_token_configured" to t(
            "Token geconfigureerd", "Token configured", "Token configurado", "Token configurado", "Token konfiguriert", "Jeton configuré", "Token configurato", "토큰 구성됨", "令牌已配置", "トークン設定済み",
            "Токен настроен", "تم تكوين الرمز", "टोकन कॉन्फ़िगर किया गया", "Token yapılandırıldı", "Token skonfigurowany", "Token dikonfigurasi", "Токен налаштовано", "Đã cấu hình token"
        ),
        "ha_not_configured" to t(
            "Nog niet geconfigureerd", "Not yet configured", "Aún no configurado", "Ainda não configurado", "Noch nicht konfiguriert", "Pas encore configuré", "Non ancora configurato", "아직 구성되지 않음", "尚未配置", "まだ設定されていません",
            "Еще не настроено", "لم يتم التكوين بعد", "अभी तक कॉन्फ़िगर नहीं किया गया", "Henüz yapılandırılmadı", "Jeszcze nie skonfigurowano", "Belum dikonfigurasi", "Ще не налаштовано", "Chưa cấu hình"
        ),
        "ha_entities_add_desc" to t(
            "Voeg handige entiteiten toe die je wilt gebruiken.", "Add useful entities you want to use.", "Agregue entidades útiles que desee usar.", "Adicione entidades úteis que deseja usar.", "Fügen Sie nützliche Entitäten hinzu, die Sie verwenden möchten.", "Ajoutez des entités utiles que vous souhaitez utiliser.", "Aggiungi entità utili che vuoi usare.", "사용하려는 유용한 엔티티를 추가하세요.", "添加您想使用的有用实体。", "使用したい便利なエンティティを追加します。",
            "Добавьте полезные сущности, которые хотите использовать.", "أضف كيانات مفيدة تريد استخدامها.", "उपयोगी इकाइयाँ जोड़ें जिन्हें आप उपयोग करना चाहते हैं।", "Kullanmak istediğiniz yararlı varlıkları ekleyin.", "Dodaj przydatne encje, których chcesz użyć.", "Tambahkan entitas berguna yang ingin Anda gunakan.", "Додайте корисні сутності, які хочете використовувати.", "Thêm các thực thể hữu ích bạn muốn sử dụng."
        ),
        "ha_speaker_select" to t(
            "Selecteer speaker.", "Select speaker.", "Seleccionar altavoz.", "Selecionar alto-falante.", "Lautsprecher auswählen.", "Sélectionner haut-parleur.", "Seleziona altoparlante.", "스피커 선택.", "选择扬声器。", "スピーカーを選択。",
            "Выберите динамик.", "حدد مكبر الصوت.", "स्पीकर चुनें।", "Hoparlör seçin.", "Wybierz głośnik.", "Pilih speaker.", "Виберіть динамік.", "Chọn loa."
        ),
        "ha_battery_usage_title" to t(
            "Batterijverbruik per uur (%)", "Battery usage per hour (%)", "Uso de batería por hora (%)", "Uso de bateria por hora (%)", "Akkuverbrauch pro Stunde (%)", "Consommation batterie par heure (%)", "Consumo batteria all'ora (%)", "시간당 배터리 사용량 (%)", "每小时电池使用量 (%)", "1時間あたりのバッテリー使用量 (%)",
            "Расход батареи в час (%)", "استهلاك البطارية في الساعة (%)", "प्रति घंटे बैटरी उपयोग (%)", "Saatlik pil kullanımı (%)", "Zużycie baterii na godzinę (%)", "Penggunaan baterai per jam (%)", "Використання батареї на годину (%)", "Sử dụng pin mỗi giờ (%)"
        ),
        "ha_backup_alarm_coming_later" to t(
            "Backup-alarmfunctie volgt in een toekomstige update.",
            "The backup alarm feature will be added in a future update.",
            "La función de alarma de respaldo se añadirá en una actualización futura.",
            "O recurso de alarme de backup será adicionado numa atualização futura.",
            "Die Backup-Alarmfunktion wird in einem zukünftigen Update ergänzt.",
            "La fonction d'alarme de secours sera ajoutée dans une prochaine mise à jour.",
            "La funzione di allarme di backup sarà aggiunta in un aggiornamento futuro.",
            "백업 알람 기능은 이후 업데이트에서 제공됩니다.",
            "备用闹钟功能将在后续更新中加入。",
            "バックアップアラーム機能は今後のアップデートで追加予定です。",
            "Функция резервного будильника появится в будущем обновлении.",
            "ستُضاف ميزة المنبه الاحتياطي في تحديث لاحق.",
            "बैकअप अलार्म सुविधा भविष्य में अपडेट में जोड़ी जाएगी।",
            "Yedek alarm özelliği ilerideki bir güncellemede eklenecek.",
            "Funkcja alarmu zapasowego zostanie dodana w przyszłej aktualizacji.",
            "Fitur alarm cadangan akan ditambahkan di pembaruan mendatang.",
            "Функцію резервного будильника буде додано в майбутньому оновленні.",
            "Tính năng báo thức dự phòng sẽ được bổ sung trong bản cập nhật sau."
        ),
        "ha_test_connection" to t(
            "Test verbinding", "Test connection", "Probar conexión", "Testar conexão", "Verbindung testen", "Tester la connexion", "Testa connessione", "연결 테스트", "测试连接", "接続をテスト",
            "Проверить соединение", "اختبار الاتصال", "कनेक्शन का परीक्षण करें", "Bağlantıyı test et", "Testuj połączenie", "Tes koneksi", "Перевірити з'єднання", "Kiểm tra kết nối"
        ),
        "ha_test_entities" to t(
            "Test entiteiten", "Test entities", "Probar entidades", "Testar entidades", "Entitäten testen", "Tester les entités", "Testa entità", "엔티티 테스트", "测试实体", "エンティティをテスト",
            "Проверить сущности", "اختبار الكيانات", "इकाइयों का परीक्षण करें", "Varlıkları test et", "Testuj encje", "Tes entitas", "Перевірити сутності", "Kiểm tra thực thể"
        ),
        "ha_x_of_x_selected" to t(
            "van", "of", "de", "de", "von", "sur", "di", "중", "的", "の",
            "из", "من", "का", "içinden", "z", "dari", "з", "trong số"
        ),
        "ha_selected" to t(
            "geselecteerd", "selected", "seleccionado", "selecionado", "ausgewählt", "sélectionné", "selezionato", "선택됨", "已选择", "選択済み",
            "выбрано", "محدد", "चयनित", "seçildi", "wybrano", "dipilih", "вибрано", "đã chọn"
        ),
        "ha_search_entity" to t(
            "Zoek entiteit...", "Search entity...", "Buscar entidad...", "Buscar entidade...", "Entität suchen...", "Rechercher entité...", "Cerca entità...", "엔티티 검색...", "搜索实体...", "エンティティを検索...",
            "Поиск сущности...", "بحث عن كيان...", "इकाई खोजें...", "Varlık ara...", "Szukaj encji...", "Cari entitas...", "Пошук сутності...", "Tìm kiếm thực thể..."
        ),
        "ha_add_entity" to t(
            "Entiteit toevoegen", "Add entity", "Agregar entidad", "Adicionar entidade", "Entität hinzufügen", "Ajouter entité", "Aggiungi entità", "엔티티 추가", "添加实体", "エンティティを追加",
            "Добавить сущность", "إضافة كيان", "इकाई जोड़ें", "Varlık ekle", "Dodaj encję", "Tambah entitas", "Додати сутність", "Thêm thực thể"
        ),
        "ha_speaker_mode" to t(
            "Speaker modus", "Speaker mode", "Modo de altavoz", "Modo de alto-falante", "Lautsprechermodus", "Mode haut-parleur", "Modalità altoparlante", "스피커 모드", "扬声器模式", "スピーカーモード",
            "Режим динамика", "وضع مكبر الصوت", "स्पीकर मोड", "Hoparlör modu", "Tryb głośnika", "Mode speaker", "Режим динаміка", "Chế độ loa"
        ),
        
        // Presence Modal
        "ha_presence_desc" to t(
            "Controleer of je thuis bent voordat het alarm af gaat.", "Check if you are home before the alarm goes off.", "Verifique si está en casa antes de que suene la alarma.", "Verifique se você está em casa antes que o alarme toque.", "Prüfen Sie, ob Sie zu Hause sind, bevor der Alarm losgeht.", "Vérifiez si vous êtes à la maison avant que l'alarme ne sonne.", "Controlla se sei a casa prima che suoni l'allarme.", "알람이 울리기 전에 집에 있는지 확인하세요.", "闹钟响起前检查您是否在家.", "アラームが鳴る前に家にいるか確認してください.",
            "Проверьте, дома ли вы, прежде чем сработает будильник.", "تحقق مما إذا كنت في المنزل قبل أن ينطلق المنبه.", "अलार्म बजने से पहले जांचें कि क्या आप घर पर हैं.", "Alarm çalmadan önce evde olup olmadığınızı kontrol edin.", "Sprawdź, czy jesteś w domu, zanim włączy się alarm.", "Periksa apakah Anda di rumah sebelum alarm berbunyi.", "Перевірте, чи ви вдома, перш ніж спрацює будильник.", "Kiểm tra xem bạn có ở nhà không trước khi báo thức kêu."
        ),
        "ha_presence_automatic" to t(
            "Automatisch", "Automatic", "Automático", "Automático", "Automatisch", "Automatique", "Automatico", "자동", "自动", "自動",
            "Автоматически", "تلقائي", "स्वचालित", "Otomatik", "Automatyczny", "Otomatis", "Автоматично", "Tự động"
        ),
        "ha_presence_status_label" to t(
            "Thuis", "Home", "En casa", "Em casa", "Zuhause", "À la maison", "A casa", "재택", "在家", "在宅",
            "Дома", "في المنزل", "घर पर", "Evde", "W domu", "Di rumah", "Вдома", "Ở nhà"
        ),
        "ha_presence_status_true" to t(
            "Waar", "True", "Verdadero", "Verdadeiro", "Wahr", "Vrai", "Vero", "참", "是", "はい",
            "Истина", "صحيح", "सही", "Doğru", "Prawda", "Benar", "Істина", "Đúng"
        ),
        "ha_presence_status_false" to t(
            "Onwaar", "False", "Falso", "Falso", "Falsch", "Faux", "Falso", "거짓", "否", "いいえ",
            "Ложь", "خطأ", "गलत", "Yanlış", "Fałsz", "Salah", "Хибно", "Sai"
        ),
        "ha_presence_status_unknown" to t(
            "Onbekend", "Unknown", "Desconocido", "Desconhecido", "Unbekannt", "Inconnu", "Sconosciuto", "알 수 없음", "未知", "不明",
            "Неизвестно", "غير معروف", "अज्ञात", "Bilinmiyor", "Nieznany", "Tidak diketahui", "Невідомо", "Không rõ"
        ),
        "ha_presence_change" to t(
            "Wijzigen", "Change", "Cambiar", "Alterar", "Ändern", "Modifier", "Modifica", "변경", "更改", "変更",
            "Изменить", "تغيير", "बदलें", "Değiştir", "Zmień", "Ubah", "Змінити", "Thay đổi"
        ),
        "ha_presence_refresh" to t(
            "Status verversen", "Refresh status", "Actualizar estado", "Atualizar estado", "Status aktualisieren", "Actualiser le statut", "Aggiorna stato", "상태 새로고침", "刷新状态", "ステータスを更新",
            "Обновить статус", "تحديث الحالة", "स्थिति ताज़ा करें", "Durumu yenile", "Odśwież status", "Segarkan status", "Оновити статус", "Làm mới trạng thái"
        ),
        "ha_out_of_bed_status_label" to t(
            "Nog in bed", "Still in bed", "Sigues en la cama", "Ainda na cama", "Noch im Bett", "Encore au lit", "Ancora a letto", "아직 침대에 있음", "还在床上", "まだベッドにいる",
            "Всё ещё в кровати", "لا يزال في السرير", "अभी भी बिस्तर पर", "Hâlâ yatakta", "Nadal w łóżku", "Masih di tempat tidur", "Досі в ліжку", "Vẫn còn trên giường"
        ),
        "ha_out_of_bed_status_off" to t(
            "Staat uit", "Off", "Desactivado", "Desativado", "Aus", "Désactivé", "Disattivato", "꺼짐", "已关闭", "オフ",
            "Выключено", "متوقف", "बंद है", "Kapalı", "Wyłączone", "Nonaktif", "Вимкнено", "Đã tắt"
        ),
        // "Waar" hergebruikt ha_presence_status_true (identieke waarde) - "Niet waar" is bewust
        // een eigen string i.p.v. ha_presence_status_false ("Onwaar"): de gebruiker wil hier
        // expliciet "Niet waar" zien, niet "Onwaar".
        "ha_out_of_bed_status_false" to t(
            "Niet waar", "Not true", "No es cierto", "Não é verdade", "Nicht wahr", "Pas vrai", "Non vero", "참이 아님", "不是", "真ではない",
            "Не так", "غير صحيح", "सच नहीं", "Doğru değil", "Nieprawda", "Tidak benar", "Не так", "Không đúng"
        ),
        "ha_home_value_question" to t(
            "Wat is de waarde als je thuis bent?", "What is the value when you are home?", "¿Cuál es el valor cuando estás en casa?", "Qual é o valor quando você está em casa?", "Was ist der Wert, wenn Sie zu Hause sind?", "Quelle est la valeur lorsque vous êtes à la maison?", "Qual è il valore quando sei a casa?", "집에 있을 때 값은 무엇입니까?", "当您在家时的值是什么？", "家にいるときの値は何ですか？",
            "Каково значение, когда вы дома?", "ما هي القيمة عندما تكون في المنزل؟", "जब आप घर पर हों तो मान क्या है?", "Evdeyken değer nedir?", "Jaka jest wartość, gdy jesteś w domu?", "Apa nilainya saat Anda di rumah?", "Яке значення, коли ви вдома?", "Giá trị khi bạn ở nhà là gì?"
        ),
        
        // Out of Bed Modal
        "ha_out_of_bed_desc" to t(
            "Wil je dat het alarm niet afgaat als je al uit bed bent?\nHoe checken we of je nog in bed ligt?",
            "Do you want the alarm to skip if you're already out of bed?\nHow do we check if you're still in bed?",
            "¿Quieres que la alarma no suene si ya estás fuera de la cama?\n¿Cómo verificamos si sigues en la cama?",
            "Você quer que o alarme não toque se já estiver fora da cama?\nComo verificamos se você ainda está na cama?",
            "Möchten Sie, dass der Alarm nicht klingelt, wenn Sie schon aus dem Bett sind?\nWie prüfen wir, ob Sie noch im Bett liegen?",
            "Voulez-vous que l'alarme ne sonne pas si vous êtes déjà hors du lit?\nComment vérifions-nous si vous êtes encore au lit?",
            "Vuoi che l'allarme non suoni se sei già fuori dal letto?\nCome verifichiamo se sei ancora a letto?",
            "이미 침대 밖에 있다면 알람을 건너뛰시겠습니까?\n아직 침대에 있는지 어떻게 확인합니까?",
            "如果您已经起床，您希望闹钟不响吗？\n我们如何检查您是否还在床上？",
            "もうベッドを出ている場合、アラームをスキップしますか？\nまだベッドにいるかどうかをどのように確認しますか？",
            "Вы хотите, чтобы будильник не звонил, если вы уже встали?\nКак мы проверяем, что вы еще в кровати?",
            "هل تريد أن يتم تخطي المنبه إذا كنت خارج السرير بالفعل؟\nكيف نتحقق من أنك لا تزال في السرير؟",
            "क्या आप चाहते हैं कि यदि आप पहले से ही बिस्तर से बाहर हैं तो अलार्म न बजे?\nहम कैसे जांचें कि आप अभी भी बिस्तर में हैं?",
            "Yataktan çıktıysanız alarmın çalmamasını ister misiniz?\nHâlâ yatakta olup olmadığınızı nasıl kontrol ederiz?",
            "Czy chcesz, aby alarm nie dzwonił, jeśli już wstałeś?\nJak sprawdzamy, czy nadal jesteś w łóżku?",
            "Apakah Anda ingin alarm tidak berbunyi jika Anda sudah keluar dari tempat tidur?\nBagaimana kami memeriksa apakah Anda masih di tempat tidur?",
            "Ви хочете, щоб будильник не дзвонив, якщо ви вже встали?\nЯк ми перевіряємо, що ви ще в ліжку?",
            "Bạn có muốn báo thức bỏ qua nếu bạn đã ra khỏi giường chưa?\nLàm thế nào để kiểm tra bạn còn ở trên giường?"
        ),
        "ha_out_of_bed_enable" to t(
            "Uit bed check inschakelen", "Enable out of bed check", "Activar verificación fuera de cama", "Ativar verificação fora da cama", "Aus-dem-Bett-Prüfung aktivieren", "Activer vérif. hors du lit", "Attiva controllo fuori dal letto", "침대 밖 확인 활성화", "启用离床检查", "ベッド外チェックを有効化",
            "Включить проверку вне кровати", "تفعيل فحص خارج السرير", "बिस्तर से बाहर जाँच सक्षम करें", "Yataktan çıkma kontrolünü etkinleştir", "Włącz sprawdzenie poza łóżkiem", "Aktifkan cek keluar dari tempat tidur", "Увімкнути перевірку поза ліжком", "Bật kiểm tra ra khỏi giường"
        ),
        "ha_select_entity" to t(
            "Selecteer entiteit", "Select entity", "Seleccionar entidad", "Selecionar entidade", "Entität auswählen", "Sélectionner entité", "Seleziona entità", "엔티티 선택", "选择实体", "エンティティを選択",
            "Выбрать сущность", "حدد كيان", "इकाई चुनें", "Varlık seç", "Wybierz encję", "Pilih entitas", "Вибрати сутність", "Chọn thực thể"
        ),
        "ha_value_when_in_bed" to t(
            "Wat is de waarde als je nog in bed ligt?", "Value when in bed", "Valor cuando en cama", "Valor quando na cama", "Wert im Bett", "Valeur au lit", "Valore a letto", "침대에 있을 때 값", "在床上时的值", "ベッドにいるときの値",
            "Значение в кровати", "القيمة عند التواجد في السرير", "बिस्तर में होने पर मान", "Yataktayken değer", "Wartość w łóżku", "Nilai saat di tempat tidur", "Значення в ліжку", "Giá trị khi ở trên giường"
        ),
        "ha_no_entities_found" to t(
            "Geen entiteiten gevonden. Voeg eerst entiteiten toe in de Entiteiten pagina.", "No entities found. Add entities first in the Entities page.", "No se encontraron entidades. Agregue entidades primero en la página Entidades.", "Nenhuma entidade encontrada. Adicione entidades primeiro na página Entidades.", "Keine Entitäten gefunden. Fügen Sie zuerst Entitäten auf der Entitäten-Seite hinzu.", "Aucune entité trouvée. Ajoutez d'abord des entités dans la page Entités.", "Nessuna entità trovata. Aggiungi prima le entità nella pagina Entità.", "엔티티를 찾을 수 없습니다. 먼저 엔티티 페이지에서 엔티티를 추가하세요.", "未找到实体。请先在实体页面添加实体。", "エンティティが見つかりません。まずエンティティページでエンティティを追加してください。",
            "Сущности не найдены. Сначала добавьте сущности на странице Сущности.", "لم يتم العثور на كيانات. أضف الكيانات أولاً في صفحة الكيانات.", "कोई इकाई नहीं मिली। पहले इकाइयाँ पृष्ठ में इकाइयाँ जोड़ें।", "Varlık bulunamadı. Önce Varlıklar sayfasında varlık ekleyin.", "Nie znaleziono encji. Najpierw dodaj encje na stronie Encje.", "Tidak ada entitas yang ditemukan. Tambahkan entitas terlebih dahulu di halaman Entitas.", "Сутності не знайдено. Спочатку додайте сутності на сторінці Сутності.", "Không tìm thấy thực thể. Thêm thực thể trước trong trang Thực thể."
        ),
        
        // Sync Button translations
        "sync_alarm_with_agenda" to t(
            "Sync Wekker met Agenda", "Sync Alarm with Calendar", "Sincronizar alarma con calendario", "Sincronizar alarme com agenda", "Wecker mit Kalender synchronisieren", "Synchroniser alarme avec agenda", "Sincronizza sveglia con agenda", "알람을 캘린더와 동기화", "将闹钟与日历同步", "アラームをカレンダーと同期",
            "Синхр. будильник с календарем", "مزامنة المنبه مع التقويم", "अलार्म को कैलेंडर के साथ सिंक करें", "Alarmı takvimle senkronize et", "Synchronizuj alarm z kalendarzem", "Sinkronkan alarm dengan kalender", "Синхр. будильник з календарем", "Đồng bộ báo thức với lịch"
        ),
        "sync_alarm_desc" to t(
            "Synchroniseer de eerstvolgende wekker en de geluidsbestanden met Home Assistant.", "Synchronize the next alarm and sound files with Home Assistant.", "Sincronice la próxima alarma y los archivos de sonido con Home Assistant.", "Sincronize o próximo alarme e arquivos de som com o Home Assistant.", "Synchronisieren Sie den nächsten Wecker und Sounddateien mit Home Assistant.", "Synchronisez la prochaine alarme et les fichiers audio avec Home Assistant.", "Sincronizza la prossima sveglia e i file audio con Home Assistant.", "다음 알람과 사운드 파일을 Home Assistant와 동기화하세요.", "将下一个闹钟和声音文件与Home Assistant同步。", "次のアラームとサウンドファイルをHome Assistantと同期します。",
            "Синхронизируйте следующий будильник и звуковые файлы с Home Assistant.", "قم بمزامنة المنبه التالي وملفات الصوت مع Home Assistant.", "अगले अलार्म और ध्वनि फ़ाइलों को Home Assistant के साथ सिंक करें।", "Bir sonraki alarmı ve ses dosyalarını Home Assistant ile senkronize edin.", "Zsynchronizuj następny alarm i pliki dźwiękowe z Home Assistant.", "Sinkronkan alarm berikutnya dan file suara dengan Home Assistant.", "Синхронізуйте наступний будильник і звукові файли з Home Assistant.", "Đồng bộ báo thức tiếp theo và tệp âm thanh với Home Assistant."
        ),
        "sync_sounds" to t(
            "Geluiden", "Sounds", "Sonidos", "Sons", "Töne", "Sons", "Suoni", "사운드", "声音", "サウンド",
            "Звуки", "الأصوات", "ध्वनियाँ", "Sesler", "Dźwięki", "Suara", "Звуки", "Âm thanh"
        ),
        "sync_device_ip" to t(
            "Device IP", "Device IP", "IP del dispositivo", "IP do dispositivo", "Geräte-IP", "IP de l'appareil", "IP dispositivo", "장치 IP", "设备IP", "デバイスIP",
            "IP устройства", "عنوان IP الجهاز", "डिवाइस IP", "Cihaz IP", "IP urządzenia", "IP Perangkat", "IP пристрою", "IP thiết bị"
        ),
        "sync_not_connected" to t(
            "Niet verbonden", "Not connected", "No conectado", "Não conectado", "Nicht verbunden", "Non connecté", "Non connesso", "연결되지 않음", "未连接", "未接続",
            "Не подключено", "غير متصل", "कनेक्ट नहीं है", "Bağlı değil", "Nie połączono", "Tidak terhubung", "Не підключено", "Chưa kết nối"
        ),
        "sync_not_configured" to t(
            "Niet geconfigureerd", "Not configured", "No configurado", "Não configurado", "Nicht konfiguriert", "Non configuré", "Non configurato", "구성되지 않음", "未配置", "未設定",
            "Не настроено", "غير مكون", "कॉन्फ़िगर नहीं किया गया", "Yapılandırılmadı", "Nie skonfigurowano", "Tidak dikonfigurasi", "Не налаштовано", "Chưa cấu hình"
        ),
        "sync_cannot_sync" to t(
            "Kan niet synchroniseren", "Cannot synchronize", "No se puede sincronizar", "Não é possível sincronizar", "Kann nicht synchronisieren", "Impossible de synchroniser", "Impossibile sincronizzare", "동기화할 수 없음", "无法同步", "同期できません",
            "Невозможно синхронизировать", "لا يمكن المزامنة", "सिंक नहीं कर सकते", "Senkronize edilemiyor", "Nie można zsynchronizować", "Tidak dapat menyinkronkan", "Неможливо синхронізувати", "Không thể đồng bộ"
        ),
        "sync_no_wifi" to t(
            "Geen WiFi verbinding", "No WiFi connection", "Sin conexión WiFi", "Sem conexão WiFi", "Keine WLAN-Verbindung", "Pas de connexion WiFi", "Nessuna connessione WiFi", "WiFi 연결 없음", "无WiFi连接", "WiFi接続なし",
            "Нет подключения WiFi", "لا يوجد اتصال WiFi", "कोई WiFi कनेक्शन नहीं", "WiFi bağlantısı yok", "Brak połączenia WiFi", "Tidak ada koneksi WiFi", "Немає підключення WiFi", "Không có kết nối WiFi"
        ),
        "sync_ha_url_not_set" to t(
            "Home Assistant URL niet ingevuld", "Home Assistant URL not set", "URL de Home Assistant no configurada", "URL do Home Assistant não definida", "Home Assistant URL nicht eingestellt", "URL Home Assistant non définie", "URL Home Assistant non impostato", "Home Assistant URL이 설정되지 않음", "Home Assistant URL未设置", "Home Assistant URLが設定されていません",
            "URL Home Assistant не установлен", "لم يتم تعيين عنوان URL لـ Home Assistant", "Home Assistant URL सेट नहीं है", "Home Assistant URL ayarlanmadı", "URL Home Assistant nie ustawiony", "URL Home Assistant tidak diatur", "URL Home Assistant не встановлено", "URL Home Assistant chưa được đặt"
        ),
        "sync_http_server_disabled" to t(
            "HTTP Server uitgeschakeld", "HTTP Server disabled", "Servidor HTTP desactivado", "Servidor HTTP desativado", "HTTP-Server deaktiviert", "Serveur HTTP désactivé", "Server HTTP disabilitato", "HTTP 서버 비활성화됨", "HTTP服务器已禁用", "HTTPサーバー無効",
            "HTTP-сервер отключен", "خادم HTTP معطل", "HTTP सर्वर अक्षम", "HTTP Sunucusu devre dışı", "Serwer HTTP wyłączony", "Server HTTP dinonaktifkan", "HTTP-сервер вимкнено", "Máy chủ HTTP bị tắt"
        ),
        "sync_no_sounds" to t(
            "Geen geluiden beschikbaar", "No sounds available", "No hay sonidos disponibles", "Nenhum som disponível", "Keine Töne verfügbar", "Aucun son disponible", "Nessun suono disponibile", "사용 가능한 사운드 없음", "没有可用的声音", "利用可能なサウンドがありません",
            "Нет доступных звуков", "لا توجد أصوات متاحة", "कोई ध्वनि उपलब्ध नहीं", "Ses yok", "Brak dostępnych dźwięków", "Tidak ada suara tersedia", "Немає доступних звуків", "Không có âm thanh khả dụng"
        ),
        "sync_location_permission_required" to t(
            "Locatie permissie vereist", "Location permission required", "Se requiere permiso de ubicación", "Permissão de localização necessária", "Standortberechtigung erforderlich", "Autorisation de localisation requise", "Autorizzazione posizione richiesta", "위치 권한 필요", "需要位置权限", "位置情報の許可が必要",
            "Требуется разрешение на местоположение", "مطلوب إذن الموقع", "स्थान अनुमति आवश्यक", "Konum izni gerekli", "Wymagane uprawnienie lokalizacji", "Izin lokasi diperlukan", "Потрібен дозвіл на місцезнаходження", "Cần quyền vị trí"
        ),
        "sync_location_permission_desc" to t(
            "Om de WiFi-naam te lezen is locatiepermissie nodig. Open app-instellingen om dit toe te staan.", "Location permission is needed to read the WiFi name. Open app settings to allow this.", "Se necesita permiso de ubicación para leer el nombre WiFi. Abra la configuración de la aplicación para permitirlo.", "A permissão de localização é necessária para ler o nome do WiFi. Abra as configurações do aplicativo para permitir.", "Standortberechtigung wird benötigt, um den WLAN-Namen zu lesen. Öffnen Sie die App-Einstellungen, um dies zu erlauben.", "L'autorisation de localisation est nécessaire pour lire le nom WiFi. Ouvrez les paramètres de l'application pour l'autoriser.", "L'autorizzazione alla posizione è necessaria per leggere il nome WiFi. Apri le impostazioni dell'app per consentirlo.", "WiFi 이름을 읽으려면 위치 권한이 필요합니다. 앱 설정을 열어 허용하세요.", "需要位置权限才能读取WiFi名称。打开应用设置以允许。", "WiFi名を読み取るには位置情報の許可が必要です。アプリ設定を開いて許可してください。",
            "Для чтения имени WiFi требуется разрешение на местоположение. Откройте настройки приложения, чтобы разрешить.", "مطلوب إذن الموقع لقراءة اسم WiFi. افتح إعدادات التطبيق للسماح بذلك.", "WiFi नाम पढ़ने के लिए स्थान अनुमति आवश्यक है। इसे अनुमति देने के लिए ऐप सेटिंग्स खोलें।", "WiFi adını okumak için konum izni gereklidir. İzin vermek için uygulama ayarlarını açın.", "Uprawnienie lokalizacji jest potrzebne do odczytu nazwy WiFi. Otwórz ustawienia aplikacji, aby zezwolić.", "Izin lokasi diperlukan untuk membaca nama WiFi. Buka pengaturan aplikasi untuk mengizinkan.", "Для читання назви WiFi потрібен дозвіл на місцезнаходження. Відкрийте налаштування програми, щоб дозволити.", "Cần quyền vị trí để đọc tên WiFi. Mở cài đặt ứng dụng để cho phép."
        ),
        "sync_open_settings" to t(
            "Open Instellingen", "Open Settings", "Abrir Configuración", "Abrir Configurações", "Einstellungen öffnen", "Ouvrir Paramètres", "Apri Impostazioni", "설정 열기", "打开设置", "設定を開く",
            "Открыть настройки", "فتح الإعدادات", "सेटिंग्स खोलें", "Ayarları Aç", "Otwórz Ustawienia", "Buka Pengaturan", "Відкрити налаштування", "Mở Cài đặt"
        ),
        "sync_success" to t(
            "Synchronisatie met Home Assistant gelukt!", "Synchronization with Home Assistant successful!", "¡Sincronización con Home Assistant exitosa!", "Sincronização com Home Assistant bem-sucedida!", "Synchronisierung mit Home Assistant erfolgreich!", "Synchronisation avec Home Assistant réussie!", "Sincronizzazione con Home Assistant riuscita!", "Home Assistant와 동기화 성공!", "与Home Assistant同步成功！", "Home Assistantとの同期に成功しました！",
            "Синхронизация с Home Assistant успешна!", "تمت المزامنة مع Home Assistant بنجاح!", "Home Assistant के साथ सिंक्रनाइज़ेशन सफल!", "Home Assistant ile senkronizasyon başarılı!", "Synchronizacja z Home Assistant powiodła się!", "Sinkronisasi dengan Home Assistant berhasil!", "Синхронізація з Home Assistant успішна!", "Đồng bộ với Home Assistant thành công!"
        ),
        
        // Modal common texts
        "unsaved_changes" to t(
            "Onopgeslagen wijzigingen", "Unsaved changes", "Cambios sin guardar", "Alterações não salvas", "Nicht gespeicherte Änderungen", "Modifications non enregistrées", "Modifiche non salvate", "저장되지 않은 변경사항", "未保存的更改", "未保存の変更",
            "Несохраненные изменения", "تغييرات غير محفوظة", "असहेजे परिवर्तन", "Kaydedilmemiş değişiklikler", "Niezapisane zmiany", "Perubahan yang belum disimpan", "Незбережені зміни", "Thay đổi chưa lưu"
        ),
        "unsaved_changes_message" to t(
            "Er zijn onopgeslagen wijzigingen. Weet je zeker dat je wilt annuleren?", "There are unsaved changes. Are you sure you want to cancel?", "Hay cambios sin guardar. ¿Estás seguro de que quieres cancelar?", "Há alterações não salvas. Tem certeza de que deseja cancelar?", "Es gibt nicht gespeicherte Änderungen. Möchten Sie wirklich abbrechen?", "Il y a des modifications non enregistrées. Êtes-vous sûr de vouloir annuler?", "Ci sono modifiche non salvate. Sei sicuro di voler annullare?", "저장되지 않은 변경사항이 있습니다. 정말 취소하시겠습니까?", "有未保存的更改。您确定要取消吗？", "保存されていない変更があります。本当にキャンセルしますか？",
            "Есть несохраненные изменения. Вы уверены, что хотите отменить?", "هناك تغييرات غير محفوظة. هل أنت متأكد أنك تريد الإلغاء؟", "असहेजे परिवर्तन हैं। क्या आप वाकई रद्द करना चाहते हैं?", "Kaydedilmemiş değişiklikler var. İptal etmek istediğinizden emin misiniz?", "Są niezapisane zmiany. Czy na pewno chcesz anulować?", "Ada perubahan yang belum disimpan. Apakah Anda yakin ingin membatalkan?", "Є незбережені зміни. Ви впевнені, що хочете скасувати?", "Có thay đổi chưa lưu. Bạn có chắc chắn muốn hủy không?"
        ),
        "no_keep_editing" to t(
            "Nee, blijf bewerken", "No, keep editing", "No, seguir editando", "Não, continuar editando", "Nein, weiter bearbeiten", "Non, continuer l'édition", "No, continua a modificare", "아니요, 계속 편집", "否，继续编辑", "いいえ、編集を続ける",
            "Нет, продолжить редактирование", "لا، استمر في التحرير", "नहीं, संपादन जारी रखें", "Hayır, düzenlemeye devam et", "Nie, kontynuuj edycję", "Tidak, lanjutkan mengedit", "Ні, продовжити редагування", "Không, tiếp tục chỉnh sửa"
        ),
        
        // Token Modal
        "token_tip_label" to t(
            "Tip: Maak een Long-Lived Token aan in Home Assistant onder Profiel > Beveiliging.", "Tip: Create a Long-Lived Token in Home Assistant under Profile > Security.", "Consejo: Cree un Token de larga duración en Home Assistant en Perfil > Seguridad.", "Dica: Crie um Token de longa duração no Home Assistant em Perfil > Segurança.", "Tipp: Erstellen Sie ein langlebiges Token in Home Assistant unter Profil > Sicherheit.", "Astuce: Créez un jeton longue durée dans Home Assistant sous Profil > Sécurité.", "Suggerimento: Crea un Token a lunga durata in Home Assistant in Profilo > Sicurezza.", "팁: 프로필 > 보안에서 Home Assistant에서 장기 토큰을 만드세요.", "提示：在 Home Assistant 的个人资料 > 安全性下创建长期令牌。", "ヒント：プロフィール > セキュリティでHome Assistantで長期トークンを作成します。",
            "Совет: Создайте долгосрочный токен в Home Assistant в разделе Профиль > Безопасность.", "نصيحة: أنشئ رمزًا طويل الأمد في Home Assistant ضمن الملف الشخصي > الأمان.", "सुझाव: प्रोफ़ाइल > सुरक्षा के तहत Home Assistant में दीर्घकालिक टोकन बनाएं।", "İpucu: Profil > Güvenlik altında Home Assistant'ta Uzun Ömürlü Belirteç oluşturun.", "Wskazówka: Utwórz długoterminowy token w Home Assistant w Profil > Bezpieczeństwo.", "Tip: Buat Token Jangka Panjang di Home Assistant di bawah Profil > Keamanan.", "Порада: Створіть довгостроковий токен у Home Assistant у розділі Профіль > Безпека.", "Mẹo: Tạo Token lâu dài trong Home Assistant trong Hồ sơ > Bảo mật."
        ),
        
        // Entities Modal
        "entities_description" to t(
            "Voeg handige entiteiten toe die je wilt gebruiken.", "Add useful entities you want to use.", "Agregue entidades útiles que desee usar.", "Adicione entidades úteis que deseja usar.", "Fügen Sie nützliche Entitäten hinzu, die Sie verwenden möchten.", "Ajoutez des entités utiles que vous souhaitez utiliser.", "Aggiungi entità utili che desideri utilizzare.", "사용하려는 유용한 엔티티를 추가하세요.", "添加您想使用的有用实体。", "使用したい便利なエンティティを追加します。",
            "Добавьте полезные сущности, которые вы хотите использовать.", "أضف الكيانات المفيدة التي تريد استخدامها.", "उपयोगी इकाइयाँ जोड़ें जिन्हें आप उपयोग करना चाहते हैं।", "Kullanmak istediğiniz yararlı varlıkları ekleyin.", "Dodaj przydatne encje, których chcesz używać.", "Tambahkan entitas berguna yang ingin Anda gunakan.", "Додайте корисні сутності, які ви хочете використовувати.", "Thêm các thực thể hữu ích mà bạn muốn sử dụng."
        ),
        
        // Speaker Modal
        "speaker_description" to t(
            "Selecteer een speaker om het alarm af te spelen.", "Select a speaker to play the alarm.", "Seleccione un altavoz para reproducir la alarma.", "Selecione um alto-falante para tocar o alarme.", "Wählen Sie einen Lautsprecher, um den Alarm abzuspielen.", "Sélectionnez un haut-parleur pour jouer l'alarme.", "Seleziona un altoparlante per riprodurre l'allarme.", "알람을 재생할 스피커를 선택하세요.", "选择扬声器播放闹钟。", "アラームを再生するスピーカーを選択します。",
            "Выберите динамик для воспроизведения будильника.", "حدد مكبر صوت لتشغيل المنبه.", "अलार्म बजाने के लिए स्पीकर चुनें।", "Alarmı çalmak için bir hoparlör seçin.", "Wybierz głośnik do odtwarzania alarmu.", "Pilih speaker untuk memutar alarm.", "Виберіть динамік для відтворення будильника.", "Chọn loa để phát báo thức."
        ),
        "restart_app" to t(
            "App opnieuw starten", "Restart app", "Reiniciar aplicación", "Reiniciar aplicativo", "App neu starten", "Redémarrer l'application", "Riavvia app", "앱 다시 시작", "重启应用", "アプリを再起動",
            "Перезапустить приложение", "إعادة تشغيل التطبيق", "ऐप पुनः प्रारंभ करें", "Uygulamayı yeniden başlat", "Uruchom ponownie aplikację", "Mulai ulang aplikasi", "Перезапустити додаток", "Khởi động lại ứng dụng"
        ),
        "no_speakers_found" to t(
            "Geen media_player entiteiten gevonden. Voeg ze eerst toe in de Entiteiten sectie.", "No media_player entities found. Add them first in the Entities section.", "No se encontraron entidades media_player. Agréguelas primero en la sección Entidades.", "Nenhuma entidade media_player encontrada. Adicione-as primeiro na seção Entidades.", "Keine media_player-Entitäten gefunden. Fügen Sie sie zuerst im Entitäten-Bereich hinzu.", "Aucune entité media_player trouvée. Ajoutez-les d'abord dans la section Entités.", "Nessuna entità media_player trovata. Aggiungile prima nella sezione Entità.", "media_player 엔티티를 찾을 수 없습니다. 먼저 엔티티 섹션에서 추가하세요.", "未找到 media_player 实体。请先在实体部分添加它们。", "media_player エンティティが見つかりません。まずエンティティセクションで追加してください。",
            "Сущности media_player не найдены. Сначала добавьте их в разделе Сущности.", "لم يتم العثور على كيانات media_player. أضفها أولاً في قسم الكيانات.", "media_player इकाइयाँ नहीं मिलीं। पहले उन्हें इकाइयाँ अनुभाग में जोड़ें।", "media_player varlıkları bulunamadı. Önce Varlıklar bölümünde ekleyin.", "Nie znaleziono encji media_player. Najpierw dodaj je w sekcji Encje.", "Tidak ada entitas media_player yang ditemukan. Tambahkan terlebih dahulu di bagian Entitas.", "Сутності media_player не знайдено. Спочатку додайте їх у розділі Сутності.", "Không tìm thấy thực thể media_player. Thêm chúng trước trong phần Thực thể."
        ),
        "select_speaker" to t(
            "Selecteer speaker", "Select speaker", "Seleccionar altavoz", "Selecionar alto-falante", "Lautsprecher auswählen", "Sélectionner haut-parleur", "Seleziona altoparlante", "스피커 선택", "选择扬声器", "スピーカーを選択",
            "Выбрать динамик", "حدد مكبر الصوت", "स्पी커 चुनें", "Hoparlör seç", "Wybierz głośnik", "Pilih speaker", "Вибрати динамік", "Chọn loa"
        ),
        "speaker_mode_label" to t(
            "Speakermodus", "Speaker mode", "Modo de altavoz", "Modo de alto-falante", "Lautsprechermodus", "Mode haut-parleur", "Modalità altoparlante", "스피커 모드", "扬声器模式", "スピーカーモード",
            "Режим динамика", "وضع مكبر الصوت", "स्पीकर मोड", "Hoparlör modu", "Tryb głośnika", "Mode speaker", "Режим динаміка", "Chế độ loa"
        ),
        "select_all" to t(
            "Selecteer alles", "Select all", "Seleccionar todo", "Selecionar tudo", "Alle auswählen", "Tout sélectionner", "Seleziona tutto", "모두 선택", "全选", "すべて選択",
            "Выбрать все", "حدد الكل", "सभी का चयन करें", "Tümünü seç", "Zaznacz wszystko", "Pilih semua", "Вибрати все", "Chọn tất cả"
        ),
        "x_of_x_selected" to t(
            "van", "of", "de", "de", "von", "sur", "di", "중", "的", "の",
            "из", "من", "का", "içinden", "z", "dari", "з", "trong số"
        ),
        "selected_count" to t(
            "geselecteerd", "selected", "seleccionado", "selecionado", "ausgewählt", "sélectionné", "selezionato", "선택됨", "已选择", "選択済み",
            "выбрано", "محدد", "चयनित", "seçildi", "wybrano", "dipilih", "вибрано", "đã chọn"
        ),
        "no_entities_available" to t(
            "Geen entiteiten beschikbaar", "No entities available", "No hay entidades disponibles", "Nenhuma entidade disponível", "Keine Entitäten verfügbar", "Aucune entité disponible", "Nessuna entità disponibile", "사용 가능한 엔티티 없음", "没有可用实体", "利用可能なエンティティがありません",
            "Нет доступных сущностей", "لا توجد كيانات متاحة", "कोई इकाई उपलब्ध नहीं", "Kullanılabilir varlık yok", "Brak dostępnych encji", "Tidak ada entitas yang tersedia", "Немає доступних сутностей", "Không có thực thể nào"
        ),
        "no_results" to t(
            "Geen resultaten", "No results", "Sin resultados", "Sem resultados", "Keine Ergebnisse", "Aucun résultat", "Nessun risultato", "결과 없음", "无结果", "結果なし",
            "Нет результатов", "لا توجد نتائج", "कोई परिणाम नहीं", "Sonuç yok", "Brak wyników", "Tidak ada hasil", "Немає результатів", "Không có kết quả"
        ),
        
        // Speaker modes
        "speaker_mode_disabled" to t(
            "Uitgeschakeld", "Disabled", "Desactivado", "Desativado", "Deaktiviert", "Désactivé", "Disabilitato", "비활성화", "已禁用", "無効",
            "Отключено", "معطل", "अक्षम", "Devre dışı", "Wyłączone", "Dinonaktifkan", "Вимкнено", "Đã tắt"
        ),
        "speaker_mode_standard" to t(
            "Standaard (hoofdalarm)", "Standard (main alarm)", "Estándar (alarma principal)", "Padrão (alarme principal)", "Standard (Hauptalarm)", "Standard (alarme principal)", "Standard (allarme principale)", "표준 (메인 알람)", "标准（主闹钟）", "標準（メインアラーム）",
            "Стандарт (основной)", "قياسي (المنبه الرئيسي)", "मानक (मुख्य अलार्म)", "Standart (ana alarm)", "Standardowy (główny alarm)", "Standar (alarm utama)", "Стандарт (основний)", "Tiêu chuẩn (báo thức chính)"
        ),
        "speaker_mode_standard_desc" to t(
            "Gebruikt de externe speaker, tenzij deze niet gevonden kan worden - dan wordt de mobiele speaker gebruikt.", "Always use the external speaker. If the speaker can't be found, the phone speaker is used instead.", "Usar siempre el altavoz externo. Si no se encuentra el altavoz, se usa el altavoz del teléfono.", "Sempre usar o alto-falante externo. Se o alto-falante não for encontrado, o alto-falante do telefone é usado.", "Immer den externen Lautsprecher verwenden. Wenn der Lautsprecher nicht gefunden wird, wird der Telefonlautsprecher verwendet.", "Toujours utiliser le haut-parleur externe. Si le haut-parleur est introuvable, le haut-parleur du téléphone est utilisé.", "Usa sempre l'altoparlante esterno. Se l'altoparlante non viene trovato, viene usato l'altoparlante del telefono.", "항상 외부 스피커 사용. 스피커를 찾을 수 없으면 휴대폰 스피커가 사용됩니다.", "始终使用外部扬声器。如果找不到扬声器，将使用手机扬声器。", "常に外部スピーカーを使用します。スピーカーが見つからない場合は、携帯電話のスピーカーが使用されます。",
            "Всегда использовать внешний динамик. Если динамик не найден, используется динамик телефона.", "استخدم دائمًا مكبر الصوت الخارجي. إذا تعذر العثور على مكبر الصوت، يُستخدم مكبر صوت الهاتف.", "हमेशा बाहरी स्पीकर का उपयोग करें। यदि स्पीकर नहीं मिलता है, तो फ़ोन स्पीकर का उपयोग किया जाएगा।", "Her zaman harici hoparlörü kullan. Hoparlör bulunamazsa telefon hoparlörü kullanılır.", "Zawsze używaj zewnętrznego głośnika. Jeśli głośnik nie zostanie znaleziony, używany jest głośnik telefonu.", "Selalu gunakan speaker eksternal. Jika speaker tidak ditemukan, speaker ponsel akan digunakan.", "Завжди використовувати зовнішній динамік. Якщо динамік не знайдено, використовується динамік телефону.", "Luôn sử dụng loa ngoài. Nếu không tìm thấy loa, loa điện thoại sẽ được sử dụng."
        ),
        "speaker_mode_backup" to t(
            "Alleen backup bij lege batterij", "Only backup when battery low", "Solo respaldo con batería baja", "Apenas backup com bateria baixa", "Nur Backup bei niedrigem Akku", "Uniquement en secours si batterie faible", "Solo backup con batteria scarica", "배터리 부족 시에만 백업", "仅在电池电量低时备份", "バッテリー残量が少ない場合のみバックアップ",
            "Только резерв при низком заряде", "النسخ الاحتياطي فقط عند انخفاض البطارية", "केवल बैटरी कम होने पर बैकअप", "Yalnızca pil azaldığında yedek", "Tylko kopia zapasowa przy niskim poziomie baterii", "Hanya cadangan saat baterai lemah", "Тільки резерв при низькому заряді", "Chỉ dự phòng khi pin yếu"
        ),
        "speaker_mode_backup_desc" to t(
            "Gebruik alleen als telefoon batterij laag is", "Use only when phone battery is low", "Usar solo cuando la batería del teléfono esté baja", "Usar apenas quando a bateria do telefone estiver baixa", "Nur verwenden, wenn der Telefonakku niedrig ist", "Utiliser uniquement lorsque la batterie du téléphone est faible", "Usa solo quando la batteria del telefono è scarica", "휴대폰 배터리가 부족할 때만 사용", "仅在手机电池电量低时使用", "電話のバッテリーが少ない場合のみ使用",
            "Использовать только при низком заряде телефона", "استخدم فقط عندما تكون بطارية الهاتف منخفضة", "केवल तभी उपयोग करें जब फ़ोन की बैटरी कम हो", "Yalnızca telefon pili düşük olduğunda kullan", "Używaj tylko, gdy bateria telefonu jest niska", "Gunakan hanya saat baterai ponsel lemah", "Використовувати лише при низькому заряді телефону", "Chỉ sử dụng khi pin điện thoại yếu"
        ),
        "speaker_mode_both" to t(
            "Beide speakers gebruiken", "Use both speakers", "Usar ambos altavoces", "Usar ambos os alto-falantes", "Beide Lautsprecher verwenden", "Utiliser les deux haut-parleurs", "Usa entrambi gli altoparlanti", "두 스피커 모두 사용", "使用两个扬声器", "両方のスピーカーを使用",
            "Использовать оба динамика", "استخدم كلا مكبري الصوت", "दोनों स्पीकर का उपयोग करें", "Her iki hoparlörü de kullan", "Używaj obu głośników", "Gunakan kedua speaker", "Використовувати обидва динаміки", "Sử dụng cả hai loa"
        ),
        "speaker_mode_both_desc" to t(
            "Alarm afgaan op externe speaker én mobiel", "Alarm on external speaker and mobile", "Alarma en altavoz externo y móvil", "Alarme no alto-falante externo e celular", "Alarm auf externem Lautsprecher und Handy", "Alarme sur haut-parleur externe et mobile", "Allarme su altoparlante esterno e cellulare", "외부 스피커와 휴대폰에서 알람", "外部扬声器和手机上的闹钟", "外部スピーカーと携帯電話でアラーム",
            "Будильник на внешнем динамике и мобильном", "المنبه على مكبر الصوت الخارجي والهاتف المحمول", "बाहरी स्पीकर और मोबाइल पर अलार्म", "Harici hoparlör ve mobilde alarm", "Alarm na zewnętrznym głośniku i telefonie", "Alarm di speaker eksternal dan ponsel", "Будильник на зовнішньому динаміку та мобільному", "Báo thức trên loa ngoài và điện thoại"
        ),
        
        // Custom alarm sounds
        "custom_alarm_sounds" to t(
            "Eigen alarmgeluiden", "Custom alarm sounds", "Sonidos de alarma personalizados", "Sons de alarme personalizados", "Benutzerdefinierte Alarmtöne", "Sons d'alarme personnalisés", "Suoni di allarme personalizzati", "맞춤 알람 소리", "自定义闹钟声音", "カスタムアラーム音",
            "Пользовательские звуки будильника", "أصوات المنبه المخصصة", "कस्टम अलार्म ध्वनियाँ", "Özel alarm sesleri", "Niestandardowe dźwięki alarmu", "Suara alarm khusus", "Власні звуки будильника", "Âm thanh báo thức tùy chỉnh"
        ),
        "custom_sounds_description" to t(
            "Voeg je eigen MP3, WAV of OGG bestanden toe als alarmgeluid (max 10 MB).", "Add your own MP3, WAV or OGG files as alarm sounds (max 10 MB).", "Agregue sus propios archivos MP3, WAV u OGG como sonidos de alarma (máx. 10 MB).", "Adicione seus próprios arquivos MP3, WAV ou OGG como sons de alarme (máx. 10 MB).", "Fügen Sie Ihre eigenen MP3-, WAV- oder OGG-Dateien als Alarmtöne hinzu (max. 10 MB).", "Ajoutez vos propres fichiers MP3, WAV ou OGG comme sons d'alarme (max 10 Mo).", "Aggiungi i tuoi file MP3, WAV o OGG come suoni di allarme (max 10 MB).", "자신의 MP3, WAV 또는 OGG 파일을 알람 소리로 추가하세요 (최대 10MB).", "添加您自己的 MP3、WAV 或 OGG 文件作为闹钟声音（最大 10 MB）。", "独自のMP3、WAV、またはOGGファイルをアラーム音として追加します（最大10 MB）。",
            "Добавьте свои файлы MP3, WAV или OGG в качестве звуков будильника (макс. 10 МБ).", "أضف ملفات MP3 أو WAV أو OGG الخاصة بك كأصوات منبه (بحد أقصى 10 ميجابايت).", "अपनी MP3, WAV या OGG फ़ाइलें अलार्म ध्वनियों के रूप में जोड़ें (अधिकतम 10 MB)।", "Kendi MP3, WAV veya OGG dosyalarınızı alarm sesi olarak ekleyin (maks. 10 MB).", "Dodaj własne pliki MP3, WAV lub OGG jako dźwięki alarmu (maks. 10 MB).", "Tambahkan file MP3, WAV, atau OGG Anda sendiri sebagai suara alarm (maks. 10 MB).", "Додайте власні файли MP3, WAV або OGG як звуки будильника (макс. 10 МБ).", "Thêm tệp MP3, WAV hoặc OGG của riêng bạn làm âm thanh báo thức (tối đa 10 MB)."
        ),
        "add_custom_sound" to t(
            "Voeg eigen geluid toe", "Add custom sound", "Agregar sonido personalizado", "Adicionar som personalizado", "Benutzerdefinierten Ton hinzufügen", "Ajouter un son personnalisé", "Aggiungi suono personalizzato", "맞춤 소리 추가", "添加自定义声音", "カスタム音を追加",
            "Добавить пользовательский звук", "إضافة صوت مخصص", "कस्टम ध्वनि जोड़ें", "Özel ses ekle", "Dodaj niestandardowy dźwięk", "Tambahkan suara khusus", "Додати власний звук", "Thêm âm thanh tùy chỉnh"
        ),
        "no_custom_sounds" to t(
            "Nog geen eigen geluiden toegevoegd", "No custom sounds added yet", "Aún no se han agregado sonidos personalizados", "Nenhum som personalizado adicionado ainda", "Noch keine benutzerdefinierten Töne hinzugefügt", "Aucun son personnalisé ajouté pour le moment", "Nessun suono personalizzato aggiunto ancora", "아직 맞춤 소리가 추가되지 않았습니다", "尚未添加自定义声音", "カスタム音はまだ追加されていません",
            "Пользовательские звуки еще не добавлены", "لم يتم إضافة أصوات مخصصة بعد", "अभी तक कोई कस्टम ध्वनि नहीं जोड़ी गई", "Henüz özel ses eklenmedi", "Nie dodano jeszcze niestandardowych dźwięków", "Belum ada suara khusus yang ditambahkan", "Власні звуки ще не додано", "Chưa thêm âm thanh tùy chỉnh nào"
        ),
        "custom_sound_added_success" to t(
            "Geluid succesvol toegevoegd!", "Sound added successfully!", "¡Sonido agregado con éxito!", "Som adicionado com sucesso!", "Ton erfolgreich hinzugefügt!", "Son ajouté avec succès!", "Suono aggiunto con successo!", "소리가 성공적으로 추가되었습니다!", "声音添加成功！", "音が正常に追加されました！",
            "Звук успешно добавлен!", "تمت إضافة الصوت بنجاح!", "ध्वनि सफलतापूर्वक जोड़ी गई!", "Ses başarıyla eklendi!", "Dźwięk dodany pomyślnie!", "Suara berhasil ditambahkan!", "Звук успішно додано!", "Đã thêm âm thanh thành công!"
        ),
        "delete_custom_sound" to t(
            "Geluid verwijderen", "Delete sound", "Eliminar sonido", "Excluir som", "Ton löschen", "Supprimer le son", "Elimina suono", "소리 삭제", "删除声音", "音を削除",
            "Удалить звук", "حذف الصوت", "ध्वनि हटाएं", "Sesi sil", "Usuń dźwięk", "Hapus suara", "Видалити звук", "Xóa âm thanh"
        ),
        "delete_custom_sound_confirm" to t(
            "Weet je zeker dat je '{name}' wilt verwijderen?", "Are you sure you want to delete '{name}'?", "¿Estás seguro de que quieres eliminar '{name}'?", "Tem certeza de que deseja excluir '{name}'?", "Möchten Sie '{name}' wirklich löschen?", "Êtes-vous sûr de vouloir supprimer '{name}'?", "Sei sicuro di voler eliminare '{name}'?", "'{name}'을(를) 삭제하시겠습니까?", "您确定要删除 '{name}' 吗？", "'{name}' を削除してもよろしいですか？",
            "Вы уверены, что хотите удалить '{name}'?", "هل أنت متأكد أنك تريد حذف '{name}'؟", "क्या आप वाकई '{name}' हटाना चाहते हैं?", "'{name}' öğesini silmek istediğinizden emin misiniz?", "Czy na pewno chcesz usunąć '{name}'?", "Apakah Anda yakin ingin menghapus '{name}'?", "Ви впевнені, що хочете видалити '{name}'?", "Bạn có chắc chắn muốn xóa '{name}' không?"
        ),
        "custom_sound_deleted" to t(
            "Geluid verwijderd", "Sound deleted", "Sonido eliminado", "Som excluído", "Ton gelöscht", "Son supprimé", "Suono eliminato", "소리 삭제됨", "声音已删除", "音が削除されました",
            "Звук удален", "تم حذف الصوت", "ध्वनि हटाई गई", "Ses silindi", "Dźwięk usunięty", "Suara dihapus", "Звук видалено", "Đã xóa âm thanh"
        ),
        "custom_sound_delete_error" to t(
            "Fout bij verwijderen geluid", "Error deleting sound", "Error al eliminar sonido", "Erro ao excluir som", "Fehler beim Löschen des Tons", "Erreur lors de la suppression du son", "Errore nell'eliminazione del suono", "소리 삭제 오류", "删除声音时出错", "音の削除エラー",
            "Ошибка удаления звука", "خطأ في حذف الصوت", "ध्वनि हटाने में त्रुटि", "Ses silinirken hata", "Błąd usuwania dźwięku", "Kesalahan menghapus suara", "Помилка видалення звуку", "Lỗi xóa âm thanh"
        ),
        "delete" to t(
            "Verwijderen", "Delete", "Eliminar", "Excluir", "Löschen", "Supprimer", "Elimina", "삭제", "删除", "削除",
            "Удалить", "حذف", "हटाएं", "Sil", "Usuń", "Hapus", "Видалити", "Xóa"
        ),
        "close" to t(
            "Sluiten", "Close", "Cerrar", "Fechar", "Schließen", "Fermer", "Chiudi", "닫기", "关闭", "閉じる",
            "Закрыть", "إغلاق", "बंद करें", "Kapat", "Zamknij", "Tutup", "Закрити", "Đóng"
        ),
        "ka_test_alarm" to t(
            "Test alarm (over 1 minuut)", "Test alarm (in 1 minute)", "Prueba de alarma (en 1 min)", "Teste de alarme (em 1 min)", "Alarm testen (in 1 Min)", "Test alarme (dans 1 min)", "Test allarme (in 1 min)", "알람 테스트 (1분 후)", "测试闹钟 (1分钟后)", "テストアラーム (1分後)",
            "Тест (через 1 мин)", "اختبار المنبه (خلال دقيقة)", "अलार्म टेस्ट (1 मिनट में)", "Alarm Testi (1 dk içinde)", "Test alarmu (za 1 min)", "Tes alarm (dalam 1 mnt)", "Тест (через 1 хв)", "Kiểm tra báo thức (trong 1 phút)"
        ),
        "ka_sync" to t(
            "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync",
            "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync", "Sync"
        ),
        "ka_syncing" to t(
            "Synchroniseren...", "Syncing...", "Sincronizando...", "Sincronizando...", "Synchronisiere...", "Synchronisation...", "Sincronizzazione...", "동기화 중...", "同步中...", "同期中...",
            "Синхронизация...", "مزامنة...", "सिंक हो रहा है...", "Senkronize ediliyor...", "Synchronizacja...", "Menyinkronkan...", "Синхронізація...", "Đang đồng bộ..."
        ),
        
        "text_color" to t(
            "Tekst Kleur", "Text Color", "Color del Texto", "Cor do Texto", "Textfarbe", "Couleur du texte", "Colore testo", "텍스트 색상", "文本颜色", "テキスト色",
            "Цвет текста", "لون النص", "पाठ का रंग", "Metin Rengi", "Kolor tekstu", "Warna Teks", "Колір тексту", "Màu văn bản"
        ),
        "button_color" to t(
            "Knop Kleur", "Button Color", "Color del Botón", "Cor do Botão", "Knopffarbe", "Couleur du bouton", "Colore pulsante", "버튼 색상", "按钮颜色", "ボタン色",
            "Цвет кнопки", "لون الزر", "बटन का रंग", "Düğme Rengi", "Kolor przycisku", "Warna Tombol", "Колір кнопки", "Màu nút"
        ),
        "button_text_color" to t(
            "Knop Tekst Kleur", "Button Text Color", "Color Texto Botón", "Cor Texto Botão", "Knopftextfarbe", "Coul. texte bouton", "Colore testo pulsante", "버튼 텍스트 색상", "按钮文本颜色", "ボタンテキスト色",
            "Цвет текста кнопки", "لون نص الزर", "बटन पाठ का रंग", "Düğme Metin Rengi", "Kolor tekstu przycisku", "Warna Teks Tombol", "Колір тексту кнопки", "Màu chữ nút"
        ),
        "background" to t(
            "Achtergrond", "Background", "Fondo", "Fundo", "Hintergrund", "Arrière-plan", "Sfondo", "배경", "背景", "背景",
            "Фон", "الخلفية", "पृष्ठभूमि", "Arkaplan", "Tło", "Latar Belakang", "Фон", "Nền"
        ),
        "navigation" to t(
            "Navigatie", "Navigation", "Navegación", "Navegação", "Navigation", "Navigation", "Navigazione", "탐색", "导航", "ナビゲーション",
            "Навигация", "التنقل", "невиगेशन", "Navigasyon", "Nawigacja", "Navigasi", "Навігація", "Điều hướng"
        ),
        "notifications" to t(
            "Meldingen", "Notifications", "Notificaciones", "Notificações", "Benachrichtigungen", "Notifications", "Notifiche", "알림", "通知", "通知",
            "Уведомления", "إشعارات", "सूचनाएं", "Bildirimler", "Powiadomienia", "Notifikasi", "Сповіщення", "Thông báo"
        ),
        "in_app_notifications" to t(
            "In-app meldingen", "In-app notifications", "Notificaciones en la app", "Notificações no app", "In-App-Benachrichtigungen", "Notifications dans l'app", "Notifiche in-app", "인앱 알림", "应用内通知", "アプリ内通知",
            "Уведомления в приложении", "إشعارات داخل التطبيق", "इन-ऐप सूचनाएं", "Uygulama içi bildirimler", "Powiadomienia w aplikacji", "Notifikasi dalam aplikasi", "Сповіщення в додатку", "Thông báo trong ứng dụng"
        ),
        "in_app_notifications_desc" to t(
            "Meldingen binnen de app tonen", "Show notifications within the app", "Mostrar notificaciones dentro de la app", "Mostrar notificações no app", "Benachrichtigungen in der App anzeigen", "Afficher les notifications dans l'app", "Mostra notifiche all'interno dell'app", "앱 내에서 알림 표시", "在应用内显示通知", "アプリ内に通知を表示",
            "Показывать уведомления в приложении", "عرض الإشعارات داخل التطبيق", "ऐप के भीतर सूचनाएं दिखाएं", "Uygulamada bildirimleri göster", "Pokaż powiadomienia w aplikacji", "Tampilkan notifikasi dalam aplikasi", "Показувати сповіщення в додатку", "Hiển thị thông báo trong ứng dụng"
        ),
        "wake_screen" to t(
            "Wek mobiel", "Wake screen", "Despertar pantalla", "Acordar tela", "Bildschirm aktivieren", "Réveiller l'écran", "Sveglia schermo", "화면 깨우기", "唤醒屏幕", "画面をウェイク",
            "Пробудить экран", "إيقاظ الشاشة", "स्क्रीन जगाएं", "Ekranı uyan", "Obudź ekran", "Bangunkan layar", "Пробудити екран", "Thức dậy màn hình"
        ),
        "wake_screen_desc" to t(
            "Scherm inschakelen wanneer een alarm afgaat", "Turn on screen when an alarm goes off", "Encender pantalla cuando suena una alarma", "Ligar tela quando um alarme toca", "Bildschirm einschalten, wenn ein Alarm ertönt", "Allumer l'écran quand une alarme sonne", "Accendi lo schermo quando suona un allarme", "알람이 울릴 때 화면 켜기", "闹钟响时打开屏幕", "アラームが鳴ったときに画面をオンにする",
            "Включить экран при срабатывании будильника", "تشغيل الشاشة عند رنين المنبه", "जब अलर्ट बजे तो स्क्रीन चालू करें", "Alarm çaldığında ekranı aç", "Włącz ekran, gdy alarm się włączy", "Nyalakan layar saat alarm berbunyi", "Увімкнути екран при спрацюванні будильника", "Bật màn hình khi báo thức phát ra"
        ),
        "full_screen_alarm_setting_desc" to t(
            "Open het alarmscherm direct via een full-screen melding als Android dit toestaat.",
            "Open the alarm screen directly through a full-screen notification when Android allows it.",
            "Abrir la pantalla de alarma con una notificación de pantalla completa si Android lo permite.",
            "Abrir o ecrã do alarme com uma notificação em ecrã inteiro se o Android permitir.",
            "Alarmbildschirm per Vollbildbenachrichtigung öffnen, wenn Android es erlaubt.",
            "Ouvrir l'écran d'alarme via une notification plein écran si Android l'autorise.",
            "Apri la schermata dell'allarme con una notifica a schermo intero se Android lo consente.",
            "Android가 허용하면 전체 화면 알림으로 알람 화면을 엽니다.",
            "如果 Android 允许，通过全屏通知打开闹钟界面。",
            "Android が許可する場合、全画面通知でアラーム画面を開きます。",
            "Открывать экран будильника через полноэкранное уведомление, если Android разрешает.",
            "افتح شاشة المنبه عبر إشعار ملء الشاشة عندما يسمح Android بذلك.",
            "Android अनुमति दे तो फुल-स्क्रीन सूचना से अलार्म स्क्रीन खोलें।",
            "Android izin verirse alarm ekranını tam ekran bildirimle aç.",
            "Otwieraj ekran alarmu przez powiadomienie pełnoekranowe, jeśli Android pozwala.",
            "Buka layar alarm lewat notifikasi layar penuh jika Android mengizinkan.",
            "Відкривати екран будильника через повноекранне сповіщення, якщо Android дозволяє.",
            "Mở màn hình báo thức qua thông báo toàn màn hình nếu Android cho phép."
        ),
        "full_screen_alarm_permission_missing" to t(
            "Android-toestemming ontbreekt; normale meldingen blijven werken.",
            "Android permission is missing; normal notifications keep working.",
            "Falta el permiso de Android; las notificaciones normales siguen funcionando.",
            "A permissão do Android falta; notificações normais continuam funcionando.",
            "Android-Berechtigung fehlt; normale Benachrichtigungen funktionieren weiter.",
            "L'autorisation Android manque ; les notifications normales continuent.",
            "Autorizzazione Android mancante; le notifiche normali continuano.",
            "Android 권한이 없습니다. 일반 알림은 계속 작동합니다.",
            "缺少 Android 权限；普通通知仍会工作。",
            "Android 権限がありません。通常通知は引き続き動作します。",
            "Нет разрешения Android; обычные уведомления продолжают работать.",
            "إذن Android مفقود؛ تستمر الإشعارات العادية في العمل.",
            "Android अनुमति नहीं है; सामान्य सूचनाएं काम करती रहेंगी।",
            "Android izni eksik; normal bildirimler çalışmaya devam eder.",
            "Brakuje uprawnienia Androida; zwykłe powiadomienia nadal działają.",
            "Izin Android belum ada; notifikasi normal tetap berfungsi.",
            "Немає дозволу Android; звичайні сповіщення працюють далі.",
            "Thiếu quyền Android; thông báo bình thường vẫn hoạt động."
        ),
        "full_screen_alarm_permission_dialog" to t(
            "Zet in Android-instellingen full-screen meldingen aan. Zonder dit werkt het alarm via normale meldingen.",
            "Enable full-screen notifications in Android settings. Without it, the alarm works through normal notifications.",
            "Activa las notificaciones de pantalla completa en Android. Sin esto, la alarma usa notificaciones normales.",
            "Ative notificações em ecrã inteiro no Android. Sem isso, o alarme usa notificações normais.",
            "Aktiviere Vollbildbenachrichtigungen in Android. Ohne dies nutzt der Alarm normale Benachrichtigungen.",
            "Activez les notifications plein écran dans Android. Sinon l'alarme utilise les notifications normales.",
            "Attiva le notifiche a schermo intero in Android. Senza, l'allarme usa notifiche normali.",
            "Android 설정에서 전체 화면 알림을 켜세요. 없으면 일반 알림으로 작동합니다.",
            "请在 Android 设置中启用全屏通知。否则闹钟使用普通通知。",
            "Android 設定で全画面通知を有効にしてください。無効時は通常通知で動作します。",
            "Включите полноэкранные уведомления в Android. Без этого используются обычные уведомления.",
            "فعّل إشعارات ملء الشاشة في إعدادات Android. بدونها يعمل المنبه عبر الإشعارات العادية.",
            "Android सेटिंग में फुल-स्क्रीन सूचनाएं चालू करें। इसके बिना सामान्य सूचनाएं काम करेंगी।",
            "Android ayarlarında tam ekran bildirimleri açın. Yoksa normal bildirimler kullanılır.",
            "Włącz powiadomienia pełnoekranowe w Androidzie. Bez tego alarm używa zwykłych powiadomień.",
            "Aktifkan notifikasi layar penuh di pengaturan Android. Tanpanya alarm memakai notifikasi normal.",
            "Увімкніть повноекранні сповіщення в Android. Без цього працюють звичайні сповіщення.",
            "Bật thông báo toàn màn hình trong cài đặt Android. Nếu không, báo thức dùng thông báo bình thường."
        ),
        "draw_over_other_apps_setting_desc" to t(
            "Gebruik ondersteunde popupvensters boven andere apps. Zonder dit blijft de app via normale meldingen werken.",
            "Use supported popup windows over other apps. Without it, the app keeps working through normal notifications.",
            "Usar ventanas emergentes compatibles sobre otras apps. Sin esto, la app sigue con notificaciones normales.",
            "Usar popups suportados sobre outros apps. Sem isso, o app continua com notificações normais.",
            "Unterstützte Popupfenster über anderen Apps verwenden. Ohne dies läuft die App mit normalen Benachrichtigungen weiter.",
            "Utiliser les fenêtres popup prises en charge au-dessus des autres apps. Sinon l'app continue avec les notifications normales.",
            "Usa popup supportati sopra altre app. Senza, l'app continua con notifiche normali.",
            "지원되는 팝업을 다른 앱 위에 표시합니다. 없으면 일반 알림으로 계속 작동합니다.",
            "在其他应用上层使用支持的弹窗。否则应用继续使用普通通知。",
            "対応するポップアップを他のアプリの上に表示します。無効時は通常通知で動作します。",
            "Использовать поддерживаемые popup-окна поверх других приложений. Без этого приложение работает через обычные уведомления.",
            "استخدم النوافذ المنبثقة المدعومة فوق التطبيقات الأخرى. بدونها يستمر التطبيق عبر الإشعارات العادية.",
            "समर्थित पॉपअप को अन्य ऐप्स के ऊपर उपयोग करें। इसके बिना ऐप सामान्य सूचनाओं से चलता रहेगा।",
            "Desteklenen açılır pencereleri diğer uygulamaların üzerinde kullan. Yoksa normal bildirimlerle çalışır.",
            "Używaj obsługiwanych okien popup nad innymi aplikacjami. Bez tego aplikacja działa przez zwykłe powiadomienia.",
            "Gunakan popup yang didukung di atas aplikasi lain. Tanpanya aplikasi tetap berjalan lewat notifikasi normal.",
            "Використовувати підтримувані popup-вікна поверх інших програм. Без цього застосунок працює через звичайні сповіщення.",
            "Dùng cửa sổ bật lên được hỗ trợ trên ứng dụng khác. Nếu không, ứng dụng vẫn dùng thông báo bình thường."
        ),
        "draw_over_other_apps_permission_missing" to t(
            "Overlay-toestemming ontbreekt; overlayvensters worden niet gebruikt.",
            "Overlay permission is missing; overlay windows will not be used.",
            "Falta el permiso de superposición; no se usarán ventanas overlay.",
            "A permissão de sobreposição falta; janelas overlay não serão usadas.",
            "Overlay-Berechtigung fehlt; Overlayfenster werden nicht verwendet.",
            "L'autorisation de superposition manque ; les fenêtres overlay ne seront pas utilisées.",
            "Autorizzazione overlay mancante; le finestre overlay non saranno usate.",
            "오버레이 권한이 없습니다. 오버레이 창은 사용되지 않습니다.",
            "缺少悬浮窗权限；不会使用覆盖窗口。",
            "オーバーレイ権限がありません。オーバーレイウィンドウは使用されません。",
            "Нет разрешения overlay; окна поверх приложений не будут использоваться.",
            "إذن الظهور فوق التطبيقات مفقود؛ لن تُستخدم نوافذ overlay.",
            "ओवरले अनुमति नहीं है; ओवरले विंडो उपयोग नहीं होंगी।",
            "Overlay izni eksik; overlay pencereleri kullanılmayacak.",
            "Brakuje uprawnienia nakładki; okna overlay nie będą używane.",
            "Izin overlay belum ada; jendela overlay tidak akan digunakan.",
            "Немає дозволу overlay; overlay-вікна не використовуватимуться.",
            "Thiếu quyền overlay; cửa sổ overlay sẽ không được dùng."
        ),
        "draw_over_other_apps_permission_dialog" to t(
            "Zet in Android-instellingen 'Weergeven boven andere apps' aan om ondersteunde overlayvensters te gebruiken.",
            "Enable 'Display over other apps' in Android settings to use supported overlay windows.",
            "Activa 'Mostrar sobre otras apps' en Android para usar ventanas overlay compatibles.",
            "Ative 'Mostrar sobre outros apps' no Android para usar janelas overlay suportadas.",
            "Aktiviere 'Über anderen Apps anzeigen' in Android, um unterstützte Overlayfenster zu nutzen.",
            "Activez 'Afficher par-dessus les autres apps' dans Android pour utiliser les fenêtres overlay prises en charge.",
            "Attiva 'Mostra sopra altre app' in Android per usare finestre overlay supportate.",
            "지원되는 오버레이 창을 사용하려면 Android 설정에서 '다른 앱 위에 표시'를 켜세요.",
            "请在 Android 设置中启用“在其他应用上层显示”以使用支持的悬浮窗。",
            "対応するオーバーレイを使うには Android 設定で「他のアプリの上に表示」を有効にしてください。",
            "Включите 'Показывать поверх других приложений' в Android, чтобы использовать поддерживаемые overlay-окна.",
            "فعّل 'العرض فوق التطبيقات الأخرى' في إعدادات Android لاستخدام نوافذ overlay المدعومة.",
            "समर्थित ओवरले विंडो के लिए Android सेटिंग में 'अन्य ऐप्स के ऊपर दिखाएं' चालू करें।",
            "Desteklenen overlay pencereleri için Android ayarlarında 'Diğer uygulamaların üzerinde göster'i açın.",
            "Włącz 'Wyświetlaj nad innymi aplikacjami' w Androidzie, aby używać obsługiwanych okien overlay.",
            "Aktifkan 'Tampilkan di atas aplikasi lain' di pengaturan Android untuk memakai jendela overlay.",
            "Увімкніть 'Показувати поверх інших програм' в Android, щоб використовувати підтримувані overlay-вікна.",
            "Bật 'Hiển thị trên ứng dụng khác' trong cài đặt Android để dùng cửa sổ overlay được hỗ trợ."
        ),
        "permission_open_android_settings" to t(
            "Open Android-instellingen", "Open Android settings", "Abrir ajustes de Android", "Abrir configurações do Android", "Android-Einstellungen öffnen", "Ouvrir les paramètres Android", "Apri impostazioni Android", "Android 설정 열기", "打开 Android 设置", "Android 設定を開く",
            "Открыть настройки Android", "فتح إعدادات Android", "Android सेटिंग खोलें", "Android ayarlarını aç", "Otwórz ustawienia Androida", "Buka pengaturan Android", "Відкрити налаштування Android", "Mở cài đặt Android"
        ),
        "permission_open_settings_short" to t(
            "Openen", "Open", "Abrir", "Abrir", "Öffnen", "Ouvrir", "Apri", "열기", "打开", "開く",
            "Открыть", "فتح", "खोलें", "Aç", "Otwórz", "Buka", "Відкрити", "Mở"
        ),
        "text_alignment" to t(
            "Tekst Uitlijning", "Text Alignment", "Alineación Texto", "Alinhamento Texto", "Textausrichtung", "Alignement texte", "Allineamento testo", "텍스트 정렬", "文本对齐", "テキスト配置",
            "Выравнивание", "محاذاة النص", "पाठ संरेखण", "Metin Hizalama", "Wyrównanie tekstu", "Perataan Teks", "Вирівнювання", "Căn chỉnh văn bản"
        ),
        "clock_layout" to t(
            "Klok Layout", "Clock Layout", "Diseño Reloj", "Layout Relógio", "Uhr-Layout", "Disposition horloge", "Layout orologio", "시계 레이아웃", "时钟布局", "時計レイアウト",
            "Макет часов", "تخطيط الساعة", "घड़ी लेआउट", "Saat Düzeni", "Układ zegara", "Tata Letak Jam", "Макет годинника", "Bố cục đồng hồ"
        ),
        "seconds_display" to t(
            "Seconden Weergave", "Seconds Display", "Mostrar Segundos", "Exibir Segundos", "Sekundenanzeige", "Affichage secondes", "Visualizza secondi", "초 표시", "秒显示", "秒表示",
            "Отобр. секунд", "عرض الثواني", "सेकंड प्रदर्शन", "Saniye Gösterimi", "Wyświetlanie sekund", "Tampilan Detik", "Відобр. секунд", "Hiển thị giây"
        ),
        "reset_default" to t(
            "Reset naar Standaard", "Reset to Default", "Restablecer", "Redefinir padrão", "Zurücksetzen", "Rétablir défaut", "Ripristina default", "기본값으로 재설정", "恢复默认", "デフォルトに戻す",
            "Сбросить", "إعادة تعيين", "रीसेट करें", "Varsayılana Sıfırla", "Przywróć domyślne", "Atur Ulang", "Скинути", "Đặt lại mặc định"
        ),
        "pick_color" to t(
            "Kleur Kiezen", "Pick Color", "Elegir Color", "Escolher Cor", "Farbe wählen", "Choisir couleur", "Scegli colore", "색상 선택", "选择颜色", "色を選択",
            "Выбрать цвет", "اختر لونًا", "रंग चुनें", "Renk Seç", "Wybierz kolor", "Pilih Warna", "Вибрати колір", "Chọn màu"
        ),
        "hex_code" to t(
            "Hex Code", "Hex Code", "Código Hex", "Código Hex", "Hex-Code", "Code Hex", "Codice Hex", "Hex 코드", "十六进制代码", "Hexコード",
            "Hex код", "رمز Hex", "हेक्स कोड", "Hex Kodu", "Kod Hex", "Kode Hex", "Hex код", "Mã Hex"
        ),
        "countdown_seconds" to t(
            "Aftelklok seconden", "Countdown seconds", "Segundos cuenta atrás", "Segundos contagem", "Countdown-Sekunden", "Secondes compte à rebours", "Secondi conto alla rovescia", "카운트다운 초", "倒计时秒", "カウントダウン秒",
            "Сек. таймера", "ثواني العد التنازلي", "उल्टी गिनती सेकंड", "Geri Sayım Saniyeleri", "Sekundy odliczania", "Detik Hitung Mundur", "Сек. таймера", "Giây đếm ngược"
        ),
        "current_time_seconds" to t(
            "Huidige tijd seconden", "Current time seconds", "Segundos hora actual", "Segundos hora atual", "Aktuelle Zeit Sekunden", "Secondes heure actuelle", "Secondi ora attuale", "현재 시간 초", "当前时间秒", "現在時刻秒",
            "Сек. текущего времени", "ثواني الوقت الحالي", "वर्तमान समय सेकंड", "Şimdiki Zaman Saniyeleri", "Sekundy bieżącego czasu", "Detik Waktu Saat Ini", "Сек. поточного часу", "Giây thời gian hiện tại"
        ),
        "always" to t(
            "Altijd", "Always", "Siempre", "Sempre", "Immer", "Toujours", "Sempre", "항상", "总是", "常に",
            "Всегда", "دائماً", "हमेशा", "Her zaman", "Zawsze", "Selalu", "Завжди", "Luôn luôn"
        ),
        "last_5_min" to t(
            "Laatste 5 min", "Last 5 min", "Últimos 5 min", "Últimos 5 min", "Letzte 5 Min", "Dernières 5 min", "Ultimi 5 min", "마지막 5분", "最后5分钟", "最後の5分",
            "Посл. 5 мин", "آخر 5 دقائق", "अंतिम 5 मिनट", "Son 5 dk", "Ostatnie 5 min", "5 mnt terakhir", "Ост. 5 хв", "5 phút cuối"
        ),
        "never" to t(
            "Nooit", "Never", "Nunca", "Nunca", "Niemals", "Jamais", "Mai", "안함", "从不", "しない",
            "Никогда", "أبداً", "कभी नहीं", "Asla", "Nigdy", "Tidak Pernah", "Ніколи", "Không bao giờ"
        ),
        "on" to t(
            "Aan", "On", "Encendido", "Ligado", "Ein", "Marche", "On", "켜짐", "开", "オン",
            "Вкл", "تشغيل", "चालू", "Açık", "Wł", "Nyala", "Вкл", "Bật"
        ),
        "play" to t(
            "Afspelen", "Play", "Reproducir", "Reproduzir", "Abspielen", "Lire", "Riproduci", "재생", "播放", "再生",
            "Воспр.", "تشغيل", "चलाएं", "Oynat", "Odtwórz", "Putar", "Відтв.", "Phát"
        ),
        "stop" to t(
            "Stop", "Stop", "Parar", "Parar", "Stop", "Arrêter", "Stop", "중지", "停止", "停止",
            "Стоп", "إيقاف", "रोकें", "Durdur", "Stop", "Berhenti", "Стоп", "Dừng"
        ),
        "off" to t(
            "Uit", "Off", "Apagado", "Desligado", "Aus", "Arrêt", "Off", "꺼짐", "关", "オフ",
            "Выкл", "إيقاف", "बंद", "Kapalı", "Wył", "Mati", "Викл", "Tắt"
        ),
        "layout_countdown_big" to t(
            "Afteltijd (groot), Huidige tijd", "Countdown (big), Current time", "Cuenta atrás (grande)", "Contagem (grande)", "Countdown (groß)", "Compte à rebours (grand)", "Conto alla rovescia (grande)", "카운트다운 (크게)", "倒计时 (大)", "カウントダウン (大)",
            "Таймер (крупно)", "العد التنازلي (كبير)", "उल्टी गिनती (बड़ा)", "Geri Sayım (Büyük)", "Odliczanie (duże)", "Hitung Mundur (Besar)", "Таймер (великий)", "Đếm ngược (lớn)"
        ),
        "layout_current_big" to t(
            "Huidige tijd (groot), Afteltijd", "Current time (big), Countdown", "Hora actual (grande)", "Hora atual (grande)", "Aktuelle Zeit (groß)", "Heure actuelle (grand)", "Ora attuale (grande)", "현재 시간 (크게)", "当前时间 (大)", "現在時刻 (大)",
            "Время (крупно)", "الوقت الحالي (كبير)", "वर्तमान समय (बड़ा)", "Şimdiki Zaman (Büyük)", "Bieżący czas (duży)", "Waktu Saat Ini (Besar)", "Час (великий)", "Thời gian hiện tại (lớn)"
        ),
        "layout_countdown_only" to t(
            "Alleen Afteltijd", "Countdown Only", "Solo cuenta atrás", "Só contagem", "Nur Countdown", "Compte à rebours seul", "Solo conto alla rovescia", "카운트다운만", "仅倒计时", "カウントダウンのみ",
            "Только таймер", "العد التنازلي فقط", "केवल उल्टी गिनती", "Sadece Geri Sayım", "Tylko odliczanie", "Hanya Hitung Mundur", "Тільки таймер", "Chỉ đếm ngược"
        ),
        "layout_current_only" to t(
            "Alleen Huidige Tijd", "Current Time Only", "Solo hora actual", "Só hora atual", "Nur aktuelle Zeit", "Heure actuelle seule", "Solo ora attuale", "현재 시간만", "仅当前时间", "現在時刻のみ",
            "Только время", "الوقت الحالي فقط", "केवल वर्तमान समय", "Sadece Şimdiki Zaman", "Tylko bieżący czas", "Hanya Waktu Saat Ini", "Тільки час", "Chỉ thời gian hiện tại"
        ),

        // Background Settings
        "background_settings_title" to t(
            "Achtergrond Instellingen", "Background Settings", "Ajustes Fondo", "Config. Fundo", "Hintergrund-Einst.", "Paramètres d'arrière-plan", "Impostazioni Sfondo", "배경 설정", "背景设置", "背景設定",
            "Настр. фона", "إعدادات الخلفية", "पृष्ठभूमि सेटिंग्स", "Arkaplan Ayarları", "Ustawienia tła", "Pengaturan Latar Belakang", "Налаштування фону", "Cài đặt nền"
        ),
        "background_choose_type" to t(
            "Kies een achtergrond type", "Choose a background type", "Elija tipo de fondo", "Escolha tipo de fundo", "Hintergrundtyp wählen", "Choisir type de fond", "Scegli tipo sfondo", "배경 유형 선택", "选择背景类型", "背景タイプを選択",
            "Выберите тип фона", "اختر نوع الخلفية", "पृष्ठभूमि प्रकार चुनें", "Arkaplan türünü seçin", "Wybierz typ tła", "Pilih jenis latar belakang", "Виберіть тип фону", "Chọn loại nền"
        ),
        "color" to t(
            "Kleur", "Color", "Color", "Cor", "Farbe", "Couleur", "Colore", "색상", "颜色", "色",
            "Цвет", "لون", "रंग", "Renk", "Kolor", "Warna", "Колір", "Màu"
        ),
        "color_desc" to t(
            "Kies een effen kleur als achtergrond", "Choose a solid color as background", "Color sólido de fondo", "Cor sólida de fundo", "Einfarbiger Hintergrund", "Couleur unie", "Colore a tinta unita", "단색 배경 선택", "选择纯色背景", "単色背景を選択",
            "Сплошной цвет", "اختر لونًا ثابتًا كخلفية", "पृष्ठभूमि के रूप में ठोस रंग चुनें", "Arkaplan olarak düz renk seç", "Wybierz jednolity kolor tła", "Pilih warna solid sebagai latar belakang", "Суцільний колір", "Chọn màu đơn sắc làm nền"
        ),
        "image" to t(
            "Afbeelding", "Image", "Imagen", "Imagem", "Bild", "Image", "Immagine", "이미지", "图片", "画像",
            "Изображение", "صورة", "छवि", "Resim", "Obraz", "Gambar", "Зображення", "Hình ảnh"
        ),
        "image_desc" to t(
            "Kies een afbeelding als achtergrond", "Choose an image as background", "Imagen de fondo", "Imagem de fundo", "Bild als Hintergrund", "Image d'arrière-plan", "Immagine di sfondo", "이미지 배경 선택", "选择图片背景", "画像背景を選択",
            "Картинка как фон", "اختر صورة كخلفية", "पृष्ठभूमि के रूप में छवि चुनें", "Arkaplan olarak resim seç", "Wybierz obraz jako tło", "Pilih gambar sebagai latar belakang", "Картинка как фон", "Chọn hình ảnh làm nền"
        ),
        "gif" to t(
            "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF",
            "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF", "GIF"
        ),
        "gif_desc" to t(
            "Kies een geanimeerde GIF als achtergrond", "Choose an animated GIF as background", "GIF animado de fondo", "GIF animado de fundo", "Animiertes GIF", "GIF animé", "GIF animata", "GIF 배경 선택", "选择GIF背景", "GIF背景を選択",
            "GIF как фон", "اختر GIF متحرك كخلفية", "पृष्ठभूमि के रूप में एनिमेटेड GIF चुनें", "Arkaplan olarak hareketli GIF seç", "Wybierz animowany GIF jako tło", "Pilih GIF animasi sebagai latar belakang", "GIF как фон", "Chọn GIF động làm nền"
        ),
        "background_color_title" to t(
            "Achtergrond Kleur", "Background Color", "Color de Fondo", "Cor de Fundo", "Hintergrundfarbe", "Couleur de fond", "Colore Sfondo", "배경 색상", "背景颜色", "背景色",
            "Цвет фона", "لون الخلفية", "पृष्ठभूमि का रंग", "Arkaplan Rengi", "Kolor tła", "Warna Latar Belakang", "Колір фону", "Màu nền"
        ),

        // Alarm Settings
        "alarm_settings_title" to t(
            "Alarm Instellingen", "Alarm Settings", "Ajustes de Alarma", "Configurações de Alarme", "Alarm-Einstellungen", "Paramètres d'alarme", "Impostazioni Allarme", "알람 설정", "闹钟设置", "アラーム設定",
            "Настр. будильника", "إعدادات المنبه", "अलार्म सेटिंग्स", "Alarm Ayarları", "Ustawienia alarmu", "Pengaturan Alarm", "Налашт. будильника", "Cài đặt báo thức"
        ),
        "alarm_sound" to t(
            "Alarmgeluid", "Alarm Sound", "Sonido de Alarma", "Som do Alarme", "Alarmton", "Sonnerie d'alarme", "Suono allarme", "알람 소리", "闹钟声音", "アラーム音",
            "Звук будильника", "صوت المنبه", "अलार्म ध्वनि", "Alarm Sesi", "Dźwięk alarmu", "Suara Alarm", "Звук будильника", "Âm thanh báo thức"
        ),
        "default_alarm_sound" to t(
            "Standaard (Disco)", "Default (Disco)", "Predeterminado (Disco)", "Padrão (Disco)", "Standard (Disco)", "Par défaut (Disco)", "Predefinito (Disco)", "기본값 (디스코)", "默认（迪斯科）", "デフォルト（ディスコ）",
            "По умолчанию (Диско)", "افتراضي (ديسكو)", "डिफ़ॉल्ट (डिस्को)", "Varsayılan (Disko)", "Domyślny (Disco)", "Default (Disko)", "За замовчуванням (Диско)", "Mặc định (Disco)"
        ),
        "choose_alarm_sound" to t(
            "Kies een alarmgeluid", "Choose an alarm sound", "Elegir sonido", "Escolher som", "Alarmton wählen", "Choisir une sonnerie", "Scegli un suono", "알람 소리 선택", "选择闹钟声音", "アラーム音を選択",
            "Выбрать звук", "اختر صوت التنبيه", "एक अलार्म ध्वनि चुनें", "Bir alarm sesi seçin", "Wybierz dźwięk alarmu", "Pilih suara alarm", "Вибрати звук", "Chọn âm thanh báo thức"
        ),
        "change_alarm_sound" to t(
            "Wijzig alarmgeluid", "Change Alarm Sound", "Cambiar sonido", "Alterar som", "Alarmton ändern", "Changer sonnerie", "Cambia suono", "알람 소리 변경", "更改闹钟声音", "アラーム音を変更",
            "Изменить звук", "تغيير صوت المنبه", "अलार्म ध्वनि बदलें", "Alarm Sesini Değiştir", "Zmień dźwięk alarmu", "Ubah Suara Alarm", "Змінити звук", "Thay đổi âm thanh báo thức"
        ),
        "vibrate" to t(
            "Trillen", "Vibrate", "Vibrar", "Vibrar", "Vibrieren", "Vibrer", "Vibrazione", "진동", "震动", "バイブレーション",
            "Вибрация", "اهتزاز", "कंपन", "Titreşim", "Wibracja", "Getar", "Вібрація", "Rung"
        ),
        "snooze_time" to t(
            "Sluimertijd (minuten)", "Snooze Time (minutes)", "Tiempo de repetición (min)", "Tempo de soneca (min)", "Schlummerzeit (Min)", "Rappel (minutes)", "Tempo posponi (minuti)", "스누즈 시간 (분)", "贪睡时间 (分钟)", "スヌーズ時間 (分)",
            "Время отсрочки (мин)", "وقت الغفوة (دقائق)", "स्नूज़ समय (मिनट)", "Erteleme Süresi (dakika)", "Czas drzemki (minuty)", "Waktu Tunda (menit)", "Час відкладення (хв)", "Thời gian báo lại (phút)"
        ),
        "alarm_volume" to t(
            "Alarmvolume", "Alarm Volume", "Volumen de Alarma", "Volume do Alarme", "Alarm-Lautstärke", "Volume d'alarme", "Volume allarme", "알람 볼륨", "闹钟音量", "アラーム音量",
            "Громкость", "حجم التنبيه", "अलार्म वॉल्यूम", "Alarm Sesi", "Głośność alarmu", "Volume Alarm", "Гучність", "Âm lượng báo thức"
        ),
        "calendar_triggers" to t(
            "Agenda Triggers", "Calendar Triggers", "Activadores de Calendario", "Gatilhos de Calendário", "Kalender-Auslöser", "Déclencheurs calendrier", "Trigger calendario", "캘린더 트리거", "日历触发器", "カレンダートリガー",
            "Триггеры календаря", "محفزات التقويم", "कैलेंडर ट्रिगर", "Takvim Tetikleyicileri", "Wyzwalacze kalendarza", "Pemicu Kalender", "Тригери календаря", "Kích hoạt lịch"
        ),
        /** Label on agenda trigger rows (was English “options” in some builds). */
        "calendar_triggers_options" to t(
            "Opties", "Options", "Opciones", "Opções", "Optionen", "Options", "Opzioni", "옵션", "选项", "オプション",
            "Опции", "خيارات", "विकल्प", "Seçenekler", "Opcje", "Opsi", "Опції", "Tùy chọn"
        ),
        "alarm_mode" to t(
            "Alarm Modus", "Alarm Mode", "Modo de Alarma", "Modo de Alarme", "Alarmmodus", "Mode d'alarme", "Modalità allarme", "알람 모드", "闹钟模式", "アラームモード",
            "Режим будильника", "وضع التنبيه", "अलार्म मोड", "Alarm Modu", "Tryb alarmu", "Mode Alarm", "Режим будильника", "Chế độ báo thức"
        ),
        "confirm_disable_trigger_title" to t(
            "Trigger uitschakelen?", "Disable Trigger?", "¿Desactivar trigger?", "Desativar gatilho?", "Trigger deaktivieren?", "Désactiver le déclencheur?", "Disattivare trigger?", "트리거 비활성화?", "禁用触发器?", "トリガーを無効にしますか?",
            "Отключить триггер?", "تعطيل المحفز؟", "ट्रिगर अक्षम करें?", "Tetikleyiciyi devre dışı bırak?", "Wyłączyć wyzwalacz?", "Nonaktifkan Pemicu?", "Вимкнути тригер?", "Tắt kích hoạt?"
        ),
        "confirm_disable_trigger_message" to t(
            "Weet je zeker dat je '{name}' wilt uitschakelen?", "Are you sure you want to disable '{name}'?", "¿Estás seguro de que quieres desactivar '{name}'?", "Tem certeza de que deseja desativar '{name}'?", "Möchten Sie '{name}' wirklich deaktivieren?", "Êtes-vous sûr de vouloir désactiver '{name}'?", "Sei sicuro di voler disattivare '{name}'?", "'{name}'을(를) 비활성화하시겠습니까?", "您确定要禁用'{name}'吗?", "'{name}'を無効にしてもよろしいですか?",
            "Вы уверены, что хотите отключить '{name}'?", "هل أنت متأكد أنك تريد تعطيل '{name}'؟", "क्या आप वाकई '{name}' को अक्षम करना चाहते हैं?", "'{name}' öğesini devre dışı bırakmak istediğinizden emin misiniz?", "Czy na pewno chcesz wyłączyć '{name}'?", "Apakah Anda yakin ingin menonaktifkan '{name}'?", "Ви впевнені, що хочете вимкнути '{name}'?", "Bạn có chắc chắn muốn tắt '{name}' không?"
        ),
        "yes_disable" to t(
            "Ja, uitschakelen", "Yes, disable", "Sí, desactivar", "Sim, desativar", "Ja, deaktivieren", "Oui, désactiver", "Sì, disattiva", "예, 비활성화", "是的，禁用", "はい、無効にします",
            "Да, отключить", "نعم، تعطيل", "हाँ, अक्षम करें", "Evet, devre dışı bırak", "Tak, wyłącz", "Ya, nonaktifkan", "Так, вимкнути", "Có, tắt"
        ),
        "alarm_triggers" to t(
            "Alarm triggers", "Alarm triggers", "Activadores de alarma", "Gatilhos de alarme", "Alarm-Auslöser", "Déclencheurs d'alarme", "Trigger allarme", "알람 트리거", "闹钟触发器", "アラームトリガー",
            "Триггеры будильника", "محفزات المنبه", "अलार्म ट्रिगर", "Alarm Tetikleyicileri", "Wyzwalacze alarmu", "Pemicu Alarm", "Тригери будильника", "Kích hoạt báo thức"
        ),
        "info" to t(
            "Info", "Info", "Info", "Info", "Info", "Info", "Info", "정보", "信息", "情報",
            "Информация", "معلومات", "जानकारी", "Bilgi", "Informacja", "Info", "Інфо", "Thông tin"
        ),
        "witgoed_info_text" to t(
            "Stel de tijd in waarop het apparaat klaar moet zijn en hoe lang het programma duurt. De berekende tijd is wanneer u het apparaat moet starten.",
            "Set the time the appliance should be ready and the program duration. The calculated time is when you should start the appliance.",
            "Establezca la hora de finalización y la duración del programa. La hora calculada es cuando debe iniciar el aparato.",
            "Defina a hora de término e a duração do programa. A hora calculada é quando você deve iniciar o aparelho.",
            "Legen Sie fest, wann das Gerät fertig sein soll und wie lange das Programm dauert. Die berechnete Zeit ist der Startzeitpunkt.",
            "Réglez l'heure de fin et la durée du programme. L'heure calculée est celle où vous devez démarrer l'appareil.",
            "Imposta l'ora in cui l'elettrodomestico deve aver finito e la durata. L'ora calcolata è quando devi avviarlo.",
            "가전제품이 완료되어야 하는 시간과 프로그램 지속 시간을 설정하세요. 계산된 시간은 가전제품을 시작해야 하는 시간입니다.",
            "设置设备应完成的时间和程序持续时间。计算出的时间是您应该启动设备的时间。",
            "家電が終了すべき時間とプログラムの所要時間を設定します。計算された時間が、家電を開始すべき時間です。",
            "Установите время готовности и длительность программы. Расчетное время - когда нужно включить прибор.",
            "اضبط الوقت الذي يجب أن يكون فيه الجهاز جاهزًا ومدة البرنامج. الوقت المحسوب هو الوقت الذي يجب عليك فيه تشغيل الجهاز.",
            "उस समय को सेट करें जब उपकरण तैयार होना चाहिए और कार्यक्रम की अवधि। गणना किया गया समय वह है जब आपको उपकरण शुरू करना चाहिए।",
            "Cihazın hazır olması gereken saati ve program süresini ayarlayın. Hesaplanan saat, cihazı başlatmanız gereken zamandır.",
            "Ustaw czas zakończenia i czas trwania programu. Obliczony czas to moment, w którym należy włączyć urządzenie.",
            "Atur waktu kapan peralatan harus siap dan durasi program. Waktu yang dihitung adalah kapan Anda harus menyalakan peralatan.",
            "Встановіть час готовності та тривалість програми. Розрахунковий час - коли потрібно увімкнути прилад.",
            "Đặt thời gian thiết bị sẽ hoàn tất và thời lượng chương trình. Thời gian được tính toán là lúc bạn nên khởi động thiết bị."
        ),

        // Components Screen
        "start_screen" to t(
            "Startscherm:", "Start screen:", "Pantalla de inicio:", "Tela inicial:", "Startbildschirm:", "Écran de démarrage:", "Schermata iniziale:", "시작 화면:", "启动画面：", "起動画面：",
            "Стартовый экран:", "شاشة البداية:", "प्रारंभ स्क्रीन:", "Başlangıç ekranı:", "Ekran startowy:", "Layar mulai:", "Стартовий екран:", "Màn hình bắt đầu:"
        ),
        "start_screen_indicator" to t(
            " (Startscherm)", " (Start screen)", " (Pantalla de inicio)", " (Tela inicial)", " (Startbildschirm)", " (Écran de démarrage)", " (Schermata iniziale)", " (시작 화면)", " (启动画面)", " (起動画面)",
            " (Стартовый экран)", " (شاشة البداية)", " (प्रारंभ स्क्रीन)", " (Başlangıç ekranı)", " (Ekran startowy)", " (Layar mulai)", " (Стартовий екран)", " (Màn hình bắt đầu)"
        ),
        
        // Shortcuts
        "shortcuts" to t(
            "Snelkoppelingen", "Shortcuts", "Accesos directos", "Atalhos", "Verknüpfungen", "Raccourcis", "Scorciatoie", "바로가기", "快捷方式", "ショートカット",
            "Ярлыки", "اختصارات", "शॉर्टकट", "Kısayollar", "Skróty", "Pintasan", "Ярлики", "Phím tắt"
        ),
        "backup_restore" to t(
            "Backup & Overzetten", "Backup & Transfer", "Copia de seguridad y transferencia", "Backup e transferência",
            "Backup & Übertragen", "Sauvegarde & Transfert", "Backup e trasferimento", "백업 및 전송",
            "备份与转移", "バックアップと転送",
            "Резервное копирование и перенос", "النسخ الاحتياطي والنقل", "बैकअप और स्थानांतरण",
            "Yedekleme ve Aktarım", "Kopia zapasowa i przenoszenie", "Backup & Transfer",
            "Резервне копіювання та перенесення", "Sao lưu & Chuyển"
        ),
        "backup_create" to t(
            "Backup maken", "Create backup", "Crear copia de seguridad", "Criar backup", "Backup erstellen", "Créer une sauvegarde", "Crea backup", "백업 만들기", "创建备份", "バックアップを作成",
            "Создать резервную копию", "إنشاء نسخة احتياطية", "बैकअप बनाएं", "Yedek oluştur", "Utwórz kopię zapasową", "Buat cadangan", "Створити резервну копію", "Tạo bản sao lưu"
        ),
        "backup_restore_action" to t(
            "Backup terugzetten", "Restore backup", "Restaurar copia de seguridad", "Restaurar backup", "Backup wiederherstellen", "Restaurer la sauvegarde", "Ripristina backup", "백업 복원", "恢复备份", "バックアップを復元",
            "Восстановить резервную копию", "استعادة النسخة الاحتياطية", "बैकअप पुनर्स्थापित करें", "Yedeği geri yükle", "Przywróć kopię zapasową", "Pulihkan cadangan", "Відновити резервну копію", "Khôi phục bản sao lưu"
        ),
        "backup_choose_file" to t(
            "Kies backup bestand", "Choose backup file", "Elegir archivo de copia de seguridad", "Escolher arquivo de backup", "Backup-Datei wählen", "Choisir le fichier de sauvegarde", "Scegli file di backup", "백업 파일 선택", "选择备份文件", "バックアップファイルを選択",
            "Выбрать файл резервной копии", "اختر ملف النسخة الاحتياطية", "बैकअप फ़ाइल चुनें", "Yedek dosyasını seç", "Wybierz plik kopii zapasowej", "Pilih file cadangan", "Вибрати файл резервної копії", "Chọn tệp sao lưu"
        ),
        "backup_found_title" to t(
            "Gevonden backups", "Backups found", "Copias de seguridad encontradas", "Backups encontrados", "Gefundene Backups", "Sauvegardes trouvées", "Backup trovati", "발견된 백업", "找到的备份", "見つかったバックアップ",
            "Найденные резервные копии", "النسخ الاحتياطية الموجودة", "मिले हुए बैकअप", "Bulunan yedekler", "Znalezione kopie zapasowe", "Cadangan yang ditemukan", "Знайдені резервні копії", "Bản sao lưu đã tìm thấy"
        ),
        "backup_none_found" to t(
            "Geen backups op dit toestel gevonden", "No backups found on this device", "No se encontraron copias de seguridad en este dispositivo", "Nenhum backup encontrado neste dispositivo", "Keine Backups auf diesem Gerät gefunden", "Aucune sauvegarde trouvée sur cet appareil", "Nessun backup trovato su questo dispositivo", "이 기기에서 백업을 찾을 수 없습니다", "在此设备上未找到备份", "この端末にバックアップが見つかりません",
            "На этом устройстве резервные копии не найдены", "لم يتم العثور على نسخ احتياطية على هذا الجهاز", "इस डिवाइस पर कोई बैकअप नहीं मिला", "Bu cihazda yedek bulunamadı", "Nie znaleziono kopii zapasowych na tym urządzeniu", "Tidak ada cadangan yang ditemukan di perangkat ini", "На цьому пристрої резервних копій не знайдено", "Không tìm thấy bản sao lưu nào trên thiết bị này"
        ),
        "backup_browse_hint" to t(
            "Blader naar de Downloads-map of de map waar de backup staat", "Browse to the Downloads folder or the folder containing the backup", "Navegue a la carpeta de Descargas o la carpeta que contiene la copia", "Navegue até a pasta Downloads ou a pasta que contém o backup", "Navigieren Sie zum Download-Ordner oder zum Ordner mit dem Backup", "Accédez au dossier Téléchargements ou au dossier contenant la sauvegarde", "Sfoglia la cartella Download o la cartella contenente il backup", "다운로드 폴더 또는 백업이 있는 폴더로 이동하세요", "浏览到下载文件夹或包含备份的文件夹", "ダウンロードフォルダまたはバックアップのあるフォルダを参照してください",
            "Перейдите в папку «Загрузки» или в папку с резервной копией", "تصفح إلى مجلد التنزيلات أو المجلد الذي يحتوي على النسخة الاحتياطية", "डाउनलोड फ़ोल्डर या बैकअप वाले फ़ोल्डर में ब्राउज़ करें", "İndirilenler klasörüne veya yedeğin bulunduğu klasöre gidin", "Przejdź do folderu Pobrane lub folderu z kopią zapasową", "Telusuri ke folder Unduhan atau folder yang berisi cadangan", "Перейдіть до папки «Завантаження» або до папки з резервною копією", "Duyệt đến thư mục Tải xuống hoặc thư mục chứa bản sao lưu"
        ),
        "backup_delete" to t(
            "Verwijderen", "Delete", "Eliminar", "Excluir", "Löschen", "Supprimer", "Elimina", "삭제", "删除", "削除",
            "Удалить", "حذف", "हटाएं", "Sil", "Usuń", "Hapus", "Видалити", "Xóa"
        ),
        "backup_delete_all" to t(
            "Verwijder allemaal", "Delete all", "Eliminar todas", "Excluir todos", "Alle löschen", "Tout supprimer", "Elimina tutti", "모두 삭제", "全部删除", "すべて削除",
            "Удалить все", "حذف الكل", "सभी हटाएं", "Tümünü sil", "Usuń wszystkie", "Hapus semua", "Видалити всі", "Xóa tất cả"
        ),
        "backup_delete_confirm_title" to t(
            "Weet je dit zeker?", "Are you sure?", "¿Estás seguro?", "Tem certeza?", "Bist du sicher?", "Êtes-vous sûr ?", "Sei sicuro?", "확실합니까?", "确定吗？", "よろしいですか？",
            "Вы уверены?", "هل أنت متأكد؟", "क्या आप निश्चित हैं?", "Emin misiniz?", "Czy na pewno?", "Apakah Anda yakin?", "Ви впевнені?", "Bạn có chắc không?"
        ),
        "backup_delete_confirm_message" to t(
            "Deze backup wordt definitief verwijderd:\n\n%1\$s", "This backup will be permanently deleted:\n\n%1\$s", "Esta copia de seguridad se eliminará definitivamente:\n\n%1\$s", "Este backup será excluído permanentemente:\n\n%1\$s", "Dieses Backup wird endgültig gelöscht:\n\n%1\$s", "Cette sauvegarde sera définitivement supprimée :\n\n%1\$s", "Questo backup verrà eliminato definitivamente:\n\n%1\$s", "이 백업이 영구적으로 삭제됩니다:\n\n%1\$s", "此备份将被永久删除：\n\n%1\$s", "このバックアップは完全に削除されます：\n\n%1\$s",
            "Эта резервная копия будет удалена навсегда:\n\n%1\$s", "سيتم حذف هذه النسخة الاحتياطية نهائيًا:\n\n%1\$s", "यह बैकअप स्थायी रूप से हटा दिया जाएगा:\n\n%1\$s", "Bu yedek kalıcı olarak silinecek:\n\n%1\$s", "Ta kopia zapasowa zostanie trwale usunięta:\n\n%1\$s", "Cadangan ini akan dihapus secara permanen:\n\n%1\$s", "Цю резервну копію буде видалено назавжди:\n\n%1\$s", "Bản sao lưu này sẽ bị xóa vĩnh viễn:\n\n%1\$s"
        ),
        "backup_delete_all_confirm_message" to t(
            "Alle %1\$d backups op dit toestel worden definitief verwijderd.", "All %1\$d backups on this device will be permanently deleted.", "Se eliminarán definitivamente las %1\$d copias de seguridad de este dispositivo.", "Todos os %1\$d backups deste dispositivo serão excluídos permanentemente.", "Alle %1\$d Backups auf diesem Gerät werden endgültig gelöscht.", "Les %1\$d sauvegardes de cet appareil seront définitivement supprimées.", "Tutti i %1\$d backup su questo dispositivo verranno eliminati definitivamente.", "이 기기의 백업 %1\$d개가 영구적으로 삭제됩니다.", "此设备上的全部 %1\$d 个备份将被永久删除。", "この端末の %1\$d 件のバックアップがすべて完全に削除されます。",
            "Все %1\$d резервных копий на этом устройстве будут удалены навсегда.", "سيتم حذف جميع النسخ الاحتياطية البالغ عددها %1\$d على هذا الجهاز نهائيًا.", "इस डिवाइस के सभी %1\$d बैकअप स्थायी रूप से हटा दिए जाएंगे।", "Bu cihazdaki %1\$d yedeğin tamamı kalıcı olarak silinecek.", "Wszystkie %1\$d kopie zapasowe na tym urządzeniu zostaną trwale usunięte.", "Semua %1\$d cadangan di perangkat ini akan dihapus secara permanen.", "Усі %1\$d резервні копії на цьому пристрої буде видалено назавжди.", "Tất cả %1\$d bản sao lưu trên thiết bị này sẽ bị xóa vĩnh viễn."
        ),
        "backup_deleted" to t(
            "Backup verwijderd", "Backup deleted", "Copia de seguridad eliminada", "Backup excluído", "Backup gelöscht", "Sauvegarde supprimée", "Backup eliminato", "백업이 삭제되었습니다", "备份已删除", "バックアップを削除しました",
            "Резервная копия удалена", "تم حذف النسخة الاحتياطية", "बैकअप हटा दिया गया", "Yedek silindi", "Kopia zapasowa usunięta", "Cadangan dihapus", "Резервну копію видалено", "Đã xóa bản sao lưu"
        ),
        "backup_deleted_count" to t(
            "%1\$d backups verwijderd", "%1\$d backups deleted", "%1\$d copias de seguridad eliminadas", "%1\$d backups excluídos", "%1\$d Backups gelöscht", "%1\$d sauvegardes supprimées", "%1\$d backup eliminati", "백업 %1\$d개가 삭제되었습니다", "已删除 %1\$d 个备份", "%1\$d 件のバックアップを削除しました",
            "Удалено резервных копий: %1\$d", "تم حذف %1\$d نسخة احتياطية", "%1\$d बैकअप हटा दिए गए", "%1\$d yedek silindi", "Usunięto %1\$d kopii zapasowych", "%1\$d cadangan dihapus", "Видалено %1\$d резервних копій", "Đã xóa %1\$d bản sao lưu"
        ),
        "backup_delete_failed" to t(
            "Verwijderen mislukt", "Delete failed", "Error al eliminar", "Falha ao excluir", "Löschen fehlgeschlagen", "Échec de la suppression", "Eliminazione non riuscita", "삭제 실패", "删除失败", "削除に失敗しました",
            "Не удалось удалить", "فشل الحذف", "हटाना विफल", "Silme başarısız", "Usuwanie nie powiodło się", "Gagal menghapus", "Не вдалося видалити", "Xóa không thành công"
        ),
        "backup_cancel" to t(
            "Annuleren", "Cancel", "Cancelar", "Cancelar", "Abbrechen", "Annuler", "Annulla", "취소", "取消", "キャンセル",
            "Отмена", "إلغاء", "रद्द करें", "İptal", "Anuluj", "Batal", "Скасувати", "Hủy"
        ),
        "answer_no" to t(
            "Nee", "No", "No", "Não", "Nein", "Non", "No", "아니요", "否", "いいえ",
            "Нет", "لا", "नहीं", "Hayır", "Nie", "Tidak", "Ні", "Không"
        ),
        // Melding "update gevonden in Home Assistant" - bewust kort gehouden: de lijst met
        // wijzigingen eronder vertelt het echte verhaal, de tekst hoeft dat niet te herhalen.
        "ha_update_title" to t(
            "Update gevonden", "Update found", "Actualización encontrada", "Atualização encontrada", "Update gefunden", "Mise à jour trouvée", "Aggiornamento trovato", "업데이트 발견", "发现更新", "更新が見つかりました",
            "Найдено обновление", "تم العثور على تحديث", "अपडेट मिला", "Güncelleme bulundu", "Znaleziono aktualizację", "Pembaruan ditemukan", "Знайдено оновлення", "Đã tìm thấy bản cập nhật"
        ),
        "ha_update_changed_settings" to t(
            "De volgende instellingen hebben een update ontvangen vanuit Home Assistant:", "The following settings received an update from Home Assistant:", "Los siguientes ajustes recibieron una actualización desde Home Assistant:", "As seguintes configurações receberam uma atualização do Home Assistant:", "Die folgenden Einstellungen haben ein Update aus Home Assistant erhalten:", "Les paramètres suivants ont reçu une mise à jour depuis Home Assistant :", "Le seguenti impostazioni hanno ricevuto un aggiornamento da Home Assistant:", "다음 설정이 Home Assistant에서 업데이트되었습니다:", "以下设置已从 Home Assistant 收到更新：", "以下の設定が Home Assistant から更新されました:",
            "Следующие настройки получили обновление из Home Assistant:", "تلقت الإعدادات التالية تحديثًا من Home Assistant:", "निम्नलिखित सेटिंग्स को Home Assistant से अपडेट मिला है:", "Aşağıdaki ayarlar Home Assistant'tan güncelleme aldı:", "Następujące ustawienia otrzymały aktualizację z Home Assistant:", "Pengaturan berikut menerima pembaruan dari Home Assistant:", "Наступні налаштування отримали оновлення з Home Assistant:", "Các cài đặt sau đã nhận bản cập nhật từ Home Assistant:"
        ),
        "ha_update_new_entities" to t(
            "De volgende entiteiten zijn nieuw in Home Assistant:", "The following entities are new in Home Assistant:", "Las siguientes entidades son nuevas en Home Assistant:", "As seguintes entidades são novas no Home Assistant:", "Die folgenden Entitäten sind neu in Home Assistant:", "Les entités suivantes sont nouvelles dans Home Assistant :", "Le seguenti entità sono nuove in Home Assistant:", "다음 엔티티가 Home Assistant에 새로 추가되었습니다:", "以下实体在 Home Assistant 中是新的：", "以下のエンティティが Home Assistant に新しく追加されました:",
            "Следующие объекты появились в Home Assistant:", "الكيانات التالية جديدة في Home Assistant:", "निम्नलिखित एंटिटी Home Assistant में नई हैं:", "Aşağıdaki varlıklar Home Assistant'ta yeni:", "Następujące encje są nowe w Home Assistant:", "Entitas berikut baru di Home Assistant:", "Наступні сутності є новими в Home Assistant:", "Các thực thể sau là mới trong Home Assistant:"
        ),
        "ha_update_take_over" to t(
            "Overnemen?", "Apply?", "¿Aplicar?", "Aplicar?", "Übernehmen?", "Appliquer ?", "Applicare?", "적용할까요?", "要应用吗？", "適用しますか？",
            "Применить?", "هل تريد التطبيق؟", "लागू करें?", "Uygulansın mı?", "Zastosować?", "Terapkan?", "Застосувати?", "Áp dụng?"
        ),
        "ha_update_apply" to t(
            "Ja, overnemen", "Yes, apply", "Sí, aplicar", "Sim, aplicar", "Ja, übernehmen", "Oui, appliquer", "Sì, applica", "예, 적용", "是，应用", "はい、適用",
            "Да, применить", "نعم، طبّق", "हाँ, लागू करें", "Evet, uygula", "Tak, zastosuj", "Ya, terapkan", "Так, застосувати", "Có, áp dụng"
        ),
        // Melding bij het openen van de app als er een backup van een ánder toestel klaarstaat.
        "backup_found_dialog_title" to t(
            "Backup gevonden", "Backup found", "Copia de seguridad encontrada", "Backup encontrado", "Backup gefunden", "Sauvegarde trouvée", "Backup trovato", "백업 발견", "找到备份", "バックアップが見つかりました",
            "Найдена резервная копия", "تم العثور على نسخة احتياطية", "बैकअप मिला", "Yedek bulundu", "Znaleziono kopię zapasową", "Cadangan ditemukan", "Знайдено резервну копію", "Đã tìm thấy bản sao lưu"
        ),
        "backup_found_dialog_message" to t(
            "Nieuwste backup gevonden:\n\n%1\$s\n\nTerugzetten?", "Newest backup found:\n\n%1\$s\n\nRestore it?", "Copia de seguridad más reciente encontrada:\n\n%1\$s\n\n¿Restaurarla?", "Backup mais recente encontrado:\n\n%1\$s\n\nRestaurar?", "Neuestes Backup gefunden:\n\n%1\$s\n\nWiederherstellen?", "Sauvegarde la plus récente trouvée :\n\n%1\$s\n\nLa restaurer ?", "Trovato il backup più recente:\n\n%1\$s\n\nRipristinarlo?", "최신 백업을 찾았습니다:\n\n%1\$s\n\n복원할까요?", "找到最新备份：\n\n%1\$s\n\n要恢复吗？", "最新のバックアップが見つかりました:\n\n%1\$s\n\n復元しますか？",
            "Найдена последняя резервная копия:\n\n%1\$s\n\nВосстановить?", "تم العثور على أحدث نسخة احتياطية:\n\n%1\$s\n\nهل تريد استعادتها؟", "नवीनतम बैकअप मिला:\n\n%1\$s\n\nपुनर्स्थापित करें?", "En yeni yedek bulundu:\n\n%1\$s\n\nGeri yüklensin mi?", "Znaleziono najnowszą kopię zapasową:\n\n%1\$s\n\nPrzywrócić ją?", "Cadangan terbaru ditemukan:\n\n%1\$s\n\nPulihkan?", "Знайдено найновішу резервну копію:\n\n%1\$s\n\nВідновити?", "Đã tìm thấy bản sao lưu mới nhất:\n\n%1\$s\n\nKhôi phục?"
        ),
        "backup_found_dialog_message_generic" to t(
            "Er is een backup gevonden. Terugzetten?", "A backup was found. Restore it?", "Se encontró una copia de seguridad. ¿Restaurarla?", "Um backup foi encontrado. Restaurar?", "Es wurde ein Backup gefunden. Wiederherstellen?", "Une sauvegarde a été trouvée. La restaurer ?", "È stato trovato un backup. Ripristinarlo?", "백업을 찾았습니다. 복원할까요?", "找到了一个备份。要恢复吗？", "バックアップが見つかりました。復元しますか？",
            "Найдена резервная копия. Восстановить?", "تم العثور على نسخة احتياطية. هل تريد استعادتها؟", "एक बैकअप मिला। पुनर्स्थापित करें?", "Bir yedek bulundu. Geri yüklensin mi?", "Znaleziono kopię zapasową. Przywrócić ją?", "Cadangan ditemukan. Pulihkan?", "Знайдено резервну копію. Відновити?", "Đã tìm thấy một bản sao lưu. Khôi phục?"
        ),
        "backup_restore_yes" to t(
            "Ja, terugzetten", "Yes, restore", "Sí, restaurar", "Sim, restaurar", "Ja, wiederherstellen", "Oui, restaurer", "Sì, ripristina", "예, 복원", "是，恢复", "はい、復元",
            "Да, восстановить", "نعم، استعد", "हाँ, पुनर्स्थापित करें", "Evet, geri yükle", "Tak, przywróć", "Ya, pulihkan", "Так, відновити", "Có, khôi phục"
        ),
        "backup_password" to t(
            "Wachtwoord", "Password", "Contraseña", "Senha", "Passwort", "Mot de passe", "Password", "비밀번호", "密码", "パスワード",
            "Пароль", "كلمة المرور", "पासवर्ड", "Şifre", "Hasło", "Kata sandi", "Пароль", "Mật khẩu"
        ),
        "backup_confirm_password" to t(
            "Bevestig wachtwoord", "Confirm password", "Confirmar contraseña", "Confirmar senha", "Passwort bestätigen", "Confirmer le mot de passe", "Conferma password", "비밀번호 확인", "确认密码", "パスワードを確認",
            "Подтвердите пароль", "تأكيد كلمة المرور", "पासवर्ड की पुष्टि करें", "Şifreyi onayla", "Potwierdź hasło", "Konfirmasi kata sandi", "Підтвердьте пароль", "Xác nhận mật khẩu"
        ),
        "backup_selected" to t(
            "Geselecteerd:", "Selected:", "Seleccionado:", "Selecionado:", "Ausgewählt:", "Sélectionné :", "Selezionato:", "선택됨:", "已选:", "選択中:",
            "Выбрано:", "المحدد:", "चयनित:", "Seçildi:", "Wybrano:", "Dipilih:", "Вибрано:", "Đã chọn:"
        ),
        "backup_restore_button" to t(
            "Terugzetten", "Restore", "Restaurar", "Restaurar", "Wiederherstellen", "Restaurer", "Ripristina", "복원", "恢复", "復元",
            "Восстановить", "استعادة", "पुनर्स्थापित करें", "Geri yükle", "Przywróć", "Pulihkan", "Відновити", "Khôi phục"
        ),
        "backup_create_in_progress" to t(
            "Backup wordt gemaakt...", "Creating backup...", "Creando copia de seguridad...", "Criando backup...", "Backup wird erstellt...", "Création de la sauvegarde...", "Creazione backup...", "백업 만드는 중...", "正在创建备份...", "バックアップを作成中...",
            "Создание резервной копии...", "جارٍ إنشاء النسخة الاحتياطية...", "बैकअप बनाया जा रहा है...", "Yedek oluşturuluyor...", "Tworzenie kopii zapasowej...", "Membuat cadangan...", "Створення резервної копії...", "Đang tạo bản sao lưu..."
        ),
        "backup_restore_in_progress" to t(
            "Backup wordt teruggezet...", "Restoring backup...", "Restaurando copia de seguridad...", "Restaurando backup...", "Backup wird wiederhergestellt...", "Restauration de la sauvegarde...", "Ripristino backup...", "백업 복원 중...", "正在恢复备份...", "バックアップを復元中...",
            "Восстановление резервной копии...", "جارٍ استعادة النسخة الاحتياطية...", "बैकअप पुनर्स्थापित हो रहा है...", "Yedek geri yükleniyor...", "Przywracanie kopii zapasowej...", "Memulihkan cadangan...", "Відновлення резервної копії...", "Đang khôi phục bản sao lưu..."
        ),
        "backup_created_format" to t(
            "Backup gemaakt: %s", "Backup created: %s", "Copia de seguridad creada: %s", "Backup criado: %s", "Backup erstellt: %s", "Sauvegarde créée : %s", "Backup creato: %s", "백업 생성됨: %s", "备份已创建: %s", "バックアップを作成しました: %s",
            "Резервная копия создана: %s", "تم إنشاء النسخة الاحتياطية: %s", "बैकअप बनाया गया: %s", "Yedek oluşturuldu: %s", "Utworzono kopię zapasową: %s", "Cadangan dibuat: %s", "Резервну копію створено: %s", "Đã tạo bản sao lưu: %s"
        ),
        "backup_error_enter_password" to t(
            "Vul een wachtwoord in", "Enter a password", "Introduce una contraseña", "Digite uma senha", "Gib ein Passwort ein", "Saisissez un mot de passe", "Inserisci una password", "비밀번호를 입력하세요", "请输入密码", "パスワードを入力してください",
            "Введите пароль", "أدخل كلمة مرور", "पासवर्ड दर्ज करें", "Bir şifre gir", "Wpisz hasło", "Masukkan kata sandi", "Введіть пароль", "Nhập mật khẩu"
        ),
        "backup_error_enter_restore_password" to t(
            "Vul het wachtwoord in", "Enter the password", "Introduce la contraseña", "Digite a senha", "Gib das Passwort ein", "Saisissez le mot de passe", "Inserisci la password", "비밀번호를 입력하세요", "请输入密码", "パスワードを入力してください",
            "Введите пароль", "أدخل كلمة المرور", "पासवर्ड दर्ज करें", "Şifreyi gir", "Wpisz hasło", "Masukkan kata sandi", "Введіть пароль", "Nhập mật khẩu"
        ),
        "backup_error_choose_file" to t(
            "Kies eerst een backup bestand", "Choose a backup file first", "Elige primero un archivo de copia de seguridad", "Escolha primeiro um arquivo de backup", "Wähle zuerst eine Backup-Datei", "Choisissez d'abord un fichier de sauvegarde", "Scegli prima un file di backup", "먼저 백업 파일을 선택하세요", "请先选择备份文件", "先にバックアップファイルを選択してください",
            "Сначала выберите файл резервной копии", "اختر ملف النسخة الاحتياطية أولاً", "पहले बैकअप फ़ाइल चुनें", "Önce bir yedek dosyası seç", "Najpierw wybierz plik kopii zapasowej", "Pilih file cadangan terlebih dahulu", "Спочатку виберіть файл резервної копії", "Trước tiên hãy chọn tệp sao lưu"
        ),
        "backup_error_passwords_mismatch" to t(
            "Wachtwoorden komen niet overeen", "Passwords do not match", "Las contraseñas no coinciden", "As senhas não coincidem", "Passwörter stimmen nicht überein", "Les mots de passe ne correspondent pas", "Le password non corrispondono", "비밀번호가 일치하지 않습니다", "密码不匹配", "パスワードが一致しません",
            "Пароли не совпадают", "كلمتا المرور غير متطابقتين", "पासवर्ड मेल नहीं खाते", "Şifreler eşleşmiyor", "Hasła nie są zgodne", "Kata sandi tidak cocok", "Паролі не збігаються", "Mật khẩu không khớp"
        ),
        "backup_create_failed" to t(
            "Backup maken mislukt", "Failed to create backup", "No se pudo crear la copia de seguridad", "Falha ao criar backup", "Backup konnte nicht erstellt werden", "Échec de la création de la sauvegarde", "Creazione backup non riuscita", "백업 생성 실패", "创建备份失败", "バックアップの作成に失敗しました",
            "Не удалось создать резервную копию", "فشل إنشاء النسخة الاحتياطية", "बैकअप बनाने में विफल", "Yedek oluşturulamadı", "Nie udało się utworzyć kopii zapasowej", "Gagal membuat cadangan", "Не вдалося створити резервну копію", "Không thể tạo bản sao lưu"
        ),
        "backup_restore_failed" to t(
            "Backup terugzetten mislukt", "Failed to restore backup", "No se pudo restaurar la copia de seguridad", "Falha ao restaurar backup", "Backup konnte nicht wiederhergestellt werden", "Échec de la restauration de la sauvegarde", "Ripristino backup non riuscito", "백업 복원 실패", "恢复备份失败", "バックアップの復元に失敗しました",
            "Не удалось восстановить резервную копию", "فشلت استعادة النسخة الاحتياطية", "बैकअप पुनर्स्थापित करने में विफल", "Yedek geri yüklenemedi", "Nie udało się przywrócić kopii zapasowej", "Gagal memulihkan cadangan", "Не вдалося відновити резервну копію", "Không thể khôi phục bản sao lưu"
        ),
        "backup_restored_title" to t(
            "Backup teruggezet", "Backup restored", "Copia de seguridad restaurada", "Backup restaurado", "Backup wiederhergestellt", "Sauvegarde restaurée", "Backup ripristinato", "백업 복원됨", "备份已恢复", "バックアップを復元しました",
            "Резервная копия восстановлена", "تمت استعادة النسخة الاحتياطية", "बैकअप पुनर्स्थापित हुआ", "Yedek geri yüklendi", "Przywrócono kopię zapasową", "Cadangan dipulihkan", "Резервну копію відновлено", "Đã khôi phục bản sao lưu"
        ),
        "backup_restored_message" to t(
            "Backup teruggezet. De app wordt herstart.", "Backup restored. The app will restart.", "Copia de seguridad restaurada. La aplicación se reiniciará.", "Backup restaurado. O app será reiniciado.", "Backup wiederhergestellt. Die App wird neu gestartet.", "Sauvegarde restaurée. L'application va redémarrer.", "Backup ripristinato. L'app verrà riavviata.", "백업이 복원되었습니다. 앱이 다시 시작됩니다.", "备份已恢复。应用将重启。", "バックアップを復元しました。アプリを再起動します。",
            "Резервная копия восстановлена. Приложение будет перезапущено.", "تمت استعادة النسخة الاحتياطية. ستتم إعادة تشغيل التطبيق.", "बैकअप पुनर्स्थापित हो गया। ऐप फिर से शुरू होगा।", "Yedek geri yüklendi. Uygulama yeniden başlatılacak.", "Przywrócono kopię zapasową. Aplikacja zostanie uruchomiona ponownie.", "Cadangan dipulihkan. Aplikasi akan dimulai ulang.", "Резервну копію відновлено. Додаток буде перезапущено.", "Đã khôi phục bản sao lưu. Ứng dụng sẽ khởi động lại."
        ),
        "backup_error_password_empty" to t(
            "Wachtwoord mag niet leeg zijn", "Password cannot be empty", "La contraseña no puede estar vacía", "A senha não pode estar vazia", "Passwort darf nicht leer sein", "Le mot de passe ne peut pas être vide", "La password non può essere vuota", "비밀번호는 비워둘 수 없습니다", "密码不能为空", "パスワードは空にできません",
            "Пароль не может быть пустым", "لا يمكن أن تكون كلمة المرور فارغة", "पासवर्ड खाली नहीं हो सकता", "Şifre boş olamaz", "Hasło nie może być puste", "Kata sandi tidak boleh kosong", "Пароль не може бути порожнім", "Mật khẩu không được để trống"
        ),
        "backup_error_file_open_failed" to t(
            "Kan backupbestand niet openen", "Cannot open backup file", "No se puede abrir el archivo de copia de seguridad", "Não foi possível abrir o arquivo de backup", "Backup-Datei kann nicht geöffnet werden", "Impossible d'ouvrir le fichier de sauvegarde", "Impossibile aprire il file di backup", "백업 파일을 열 수 없습니다", "无法打开备份文件", "バックアップファイルを開けません",
            "Не удается открыть файл резервной копии", "لا يمكن فتح ملف النسخة الاحتياطية", "बैकअप फ़ाइल नहीं खोली जा सकती", "Yedek dosyası açılamıyor", "Nie można otworzyć pliku kopii zapasowej", "Tidak dapat membuka file cadangan", "Не вдається відкрити файл резервної копії", "Không thể mở tệp sao lưu"
        ),
        "backup_error_wrong_password" to t(
            "Verkeerd wachtwoord", "Wrong password", "Contraseña incorrecta", "Senha incorreta", "Falsches Passwort", "Mot de passe incorrect", "Password errata", "잘못된 비밀번호", "密码错误", "パスワードが違います",
            "Неверный пароль", "كلمة المرور غير صحيحة", "गलत पासवर्ड", "Yanlış şifre", "Nieprawidłowe hasło", "Kata sandi salah", "Неправильний пароль", "Sai mật khẩu"
        ),
        "shortcuts_description" to t(
            "Maak snelkoppelingen op je startscherm", "Create shortcuts on your home screen", "Crear accesos directos en la pantalla de inicio", "Criar atalhos na tela inicial", "Verknüpfungen auf dem Startbildschirm erstellen", "Créer des raccourcis sur l'écran d'accueil", "Crea scorciatoie nella schermata home", "홈 화면에 바로가기 만들기", "在主屏幕创建快捷方式", "ホーム画面にショートカットを作成",
            "Создать ярлыки на главном экране", "إنشاء اختصارات على الشاشة الرئيسية", "होम स्क्रीन पर शॉर्टकट बनाएं", "Ana ekranda kısayollar oluştur", "Utwórz skróty na ekranie głównym", "Buat pintasan di layar beranda", "Створити ярлики на головному екрані", "Tạo phím tắt trên màn hình chính"
        ),
        "shortcut_added" to t(
            "toegevoegd", "added", "añadido", "adicionado", "hinzugefügt", "ajouté", "aggiunto", "추가됨", "已添加", "追加済み",
            "добавлен", "أضيف", "जोड़ा गया", "eklendi", "dodano", "ditambahkan", "додано", "đã thêm"
        ),
        "shortcut_failed" to t(
            "Snelkoppeling maken mislukt", "Failed to create shortcut", "Error al crear acceso directo", "Falha ao criar atalho", "Verknüpfung konnte nicht erstellt werden", "Échec de la création du raccourci", "Impossibile creare la scorciatoia", "바로가기 생성 실패", "创建快捷方式失败", "ショートカットの作成に失敗",
            "Не удалось создать ярлык", "فشل في إنشاء الاختصار", "शॉर्टकट बनाने में विफल", "Kısayol oluşturulamadı", "Nie udało się utworzyć skrótu", "Gagal membuat pintasan", "Не вдалося створити ярлик", "Không thể tạo phím tắt"
        ),
        "shortcut_not_supported" to t(
            "Snelkoppelingen worden niet ondersteund op dit apparaat", "Shortcuts are not supported on this device", "Los accesos directos no son compatibles con este dispositivo", "Atalhos não são suportados neste dispositivo", "Verknüpfungen werden auf diesem Gerät nicht unterstützt", "Les raccourcis ne sont pas pris en charge sur cet appareil", "Le scorciatoie non sono supportate su questo dispositivo", "이 기기에서는 바로가기가 지원되지 않습니다", "此设备不支持快捷方式", "このデバイスではショートカットはサポートされていません",
            "Ярлыки не поддерживаются на этом устройстве", "الاختصارات غير مدعومة على هذا الجهاز", "इस डिवाइस पर शॉर्टकट समर्थित नहीं हैं", "Bu cihazda kısayollar desteklenmiyor", "Skróty nie są obsługiwane na tym urządzeniu", "Pintasan tidak didukung di perangkat ini", "Ярлики не підтримуються на цьому пристрої", "Phím tắt không được hỗ trợ trên thiết bị này"
        ),
        "home_assist" to t(
            "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant",
            "Home Assistant", "مساعد المنزل", "होम असिस्टेंट", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant", "Home Assistant"
        ),
        "popup_notification" to t(
            "Popup meldingen updaten", "Update popup notifications", "Actualizar notificaciones emergentes", "Atualizar notificações popup", "Popup-Benachrichtigungen aktualisieren", "Mettre à jour les notifications popup", "Aggiorna notifiche popup", "팝업 알림 업데이트", "更新弹出通知", "ポップアップ通知を更新",
            "Обновить всплывающие уведомления", "تحديث الإشعارات المنبثقة", "पॉपअप अधिसूचनाएं अपडेट करें", "Açılır bildirimleri güncelle", "Aktualizuj powiadomienia wyskakujące", "Perbarui notifikasi popup", "Оновити спливаючі сповіщення", "Cập nhật thông báo bật lên"
        ),
        "popup_last_30_min" to t(
            "Popup laatste 30 minuten", "Popup last 30 minutes", "Popup últimos 30 minutos", "Popup últimos 30 minutos", "Popup letzte 30 Minuten", "Popup dernières 30 minutes", "Popup ultimi 30 minuti", "마지막 30분 팝업", "最后30分钟弹出", "最後の30分ポップアップ",
            "Всплывающее последние 30 минут", "إشعار منبثق آخر 30 دقيقة", "पॉपअप अंतिम 30 मिनट", "Son 30 dakika popup", "Popup ostatnie 30 minut", "Popup 30 menit terakhir", "Спливаюче останні 30 хвилин", "Bật lên 30 phút cuối"
        ),
        "popup_last" to t(
            "Alarm melding met Stop knop", "Alarm notification with Stop button", "Notificación de alarma con botón de Parar", "Notificação de alarme com botão de Parar", "Alarm-Benachrichtigung mit Stopp-Taste", "Notification d'alarme avec bouton Arrêter", "Notifica allarme con pulsante Stop", "중지 버튼이 있는 알람 알림", "带停止按钮的闹钟通知", "停止ボタン付きアラーム通知",
            "Уведомление будильника с кнопкой Стоп", "إشعار المنبه بزر إيقاف", "स्टॉप बटन वाली अलार्म सूचना", "Durdur butonlu alarm bildirimi", "Powiadomienie alarmu z przyciskiem Stop", "Notifikasi alarm dengan tombol Berhenti", "Сповіщення будильника з кнопкою Стоп", "Thông báo báo thức có nút Dừng"
        ),
        "ka_meldingen" to t(
            "Meldingen", "Notifications", "Notificaciones", "Notificações", "Benachrichtigungen", "Notifications", "Notifiche", "알림", "通知", "通知",
            "Уведомления", "الإشعارات", "सूचनाएं", "Bildirimler", "Powiadomienia", "Notifikasi", "Сповіщення", "Thông báo"
        ),
        "buttonless_notification" to t(
            "Alarm meldingen zonder knoppen", "Alarm notifications without buttons", "Notificaciones sin botones", "Notificações sem botões", "Alarm-Benachrichtigungen ohne Tasten", "Notifications sans boutons", "Notifiche senza pulsanti", "버튼 없는 알람 알림", "无按钮闹钟通知", "ボタンなしアラーム通知",
            "Уведомления без кнопок", "إشعارات بدون أزرار", "बिना बटन सूचनाएं", "Düğmesiz bildirimler", "Powiadomienia bez przycisków", "Notifikasi tanpa tombol", "Сповіщення без кнопок", "Thông báo không nút"
        ),
        "buttonless_hours_before" to t(
            "uur van tevoren", "hours before", "horas antes", "horas antes", "Stunden vorher", "heures avant", "ore prima", "시간 전", "小时前", "時間前",
            "часов до", "ساعات قبل", "घंटे पहले", "saat önce", "godzin przed", "jam sebelum", "годин до", "giờ trước"
        ),
        "sound_playing" to t(
            "Geluid speelt af...", "Sound playing...", "Reproduciendo sonido...", "Reproduzindo som...", "Ton wird abgespielt...", "Son en cours...", "Audio in riproduzione...", "소리 재생 중...", "正在播放声音...", "音声再生中...",
            "Воспроизведение звука...", "جاري تشغيل الصوت...", "ध्वनि चल रही है...", "Ses çalınıyor...", "Odtwarzanie dźwięku...", "Memutar suara...", "Відтворення звуку...", "Đang phát âm thanh..."
        ),
        "sounds_enabled" to t(
            "Geluiden", "Sounds", "Sonidos", "Sons", "Töne", "Sons", "Suoni", "소리", "声音", "サウンド",
            "Звуки", "الأصوات", "ध्वनियाँ", "Sesler", "Dźwięki", "Suara", "Звуки", "Âm thanh"
        ),
        
        // Alarm Backup Section
        "ha_alarm_backup" to t(
            "Alarm backup", "Alarm backup", "Respaldo de alarma", "Backup de alarme", "Alarm-Backup", "Sauvegarde alarme", "Backup allarme", "알람 백업", "闹钟备份", "アラームバックアップ",
            "Резервный будильник", "نسخة احتياطية للمنبه", "अलार्म बैकअप", "Alarm Yedekleme", "Kopia zapasowa alarmu", "Cadangan Alarm", "Резервний будильник", "Sao lưu báo thức"
        ),
        "ha_alarm_backup_desc" to t(
            "Gebruik Home Assistant als backup als je telefoon het alarm niet kan afspelen.",
            "Use Home Assistant as backup if your phone cannot play the alarm.",
            "Usa Home Assistant como respaldo si tu teléfono no puede reproducir la alarma.",
            "Use o Home Assistant como backup se o telefone não puder tocar o alarme.",
            "Verwende Home Assistant als Backup, wenn dein Telefon den Alarm nicht abspielen kann.",
            "Utilisez Home Assistant comme sauvegarde si votre téléphone ne peut pas jouer l'alarme.",
            "Usa Home Assistant come backup se il telefono non può riprodurre l'allarme.",
            "휴대폰에서 알람을 재생할 수 없는 경우 Home Assistant를 백업으로 사용하세요.",
            "如果手机无法播放闹钟，请使用 Home Assistant 作为备份。",
            "電話がアラームを再生できない場合、Home Assistantをバックアップとして使用します。",
            "Используйте Home Assistant как резерв, если телефон не может воспроизвести будильник.",
            "استخدم Home Assistant كنسخة احتياطية إذا لم يتمكن هاتفك من تشغيل المنبه.",
            "यदि आपका फ़ोन अलार्म नहीं बजा सकता है तो Home Assistant को बैकअप के रूप में उपयोग करें।",
            "Telefonunuz alarmı çalamıyorsa Home Assistant'ı yedek olarak kullanın.",
            "Użyj Home Assistant jako kopii zapasowej, jeśli telefon nie może odtworzyć alarmu.",
            "Gunakan Home Assistant sebagai cadangan jika ponsel Anda tidak dapat memutar alarm.",
            "Використовуйте Home Assistant як резерв, якщо телефон не може відтворити будильник.",
            "Sử dụng Home Assistant làm bản sao lưu nếu điện thoại không thể phát báo thức."
        ),
        // Expliciete "gebruik geen HA-speaker"-keuze in de speaker-lijst zelf (Timer/Weer),
        // functioneel gelijk aan geen entiteit selecteren, zie SpeakerModal.kt.
        "ha_phone_speaker_option" to t(
            "Mobiel speaker", "Phone speaker", "Altavoz del teléfono", "Alto-falante do telefone", "Telefonlautsprecher", "Haut-parleur du téléphone", "Altoparlante del telefono", "휴대폰 스피커", "手机扬声器", "携帯電話のスピーカー",
            "Динамик телефона", "مكبر صوت الهاتف", "फ़ोन स्पीकर", "Telefon hoparlörü", "Głośnik telefonu", "Speaker ponsel", "Динамік телефону", "Loa điện thoại"
        ),
        "ha_phone_speaker_option_desc" to t(
            "Gebruik alleen de speaker van de telefoon, de HA-speaker wordt niet gebruikt.", "Use only the phone's speaker, the HA speaker is not used.", "Usa solo el altavoz del teléfono, no se usa el altavoz de HA.", "Usa apenas o alto-falante do telefone, o alto-falante do HA não é usado.", "Verwendet nur den Telefonlautsprecher, der HA-Lautsprecher wird nicht genutzt.", "Utilise uniquement le haut-parleur du téléphone, le haut-parleur HA n'est pas utilisé.", "Usa solo l'altoparlante del telefono, l'altoparlante HA non viene usato.", "휴대폰 스피커만 사용하며 HA 스피커는 사용하지 않습니다.", "仅使用手机扬声器，不使用 HA 扬声器。", "携帯電話のスピーカーのみ使用し、HAスピーカーは使用しません。",
            "Используется только динамик телефона, динамик HA не используется.", "يُستخدم مكبر صوت الهاتف فقط، ولا يُستخدم مكبر صوت HA.", "केवल फ़ोन के स्पीकर का उपयोग होगा, HA स्पीकर का उपयोग नहीं होगा।", "Yalnızca telefonun hoparlörü kullanılır, HA hoparlörü kullanılmaz.", "Używany jest tylko głośnik telefonu, głośnik HA nie jest używany.", "Hanya menggunakan speaker ponsel, speaker HA tidak digunakan.", "Використовується лише динамік телефону, динамік HA не використовується.", "Chỉ sử dụng loa điện thoại, không sử dụng loa HA."
        ),
        // Timer/Weer-variant van "ha_alarm_backup" e.a. - "Backup alarm" is een agenda-alarm-
        // specifiek concept (backup bij gemist alarm door lege batterij) en past niet bij een
        // timer of weerwaarschuwing, zie SpeakerModal.kt's isAlarmContext.
        "ha_mobile_speaker_only" to t(
            "Alleen mobiel speaker", "Phone speaker only", "Solo altavoz del teléfono", "Apenas alto-falante do telefone", "Nur Telefonlautsprecher", "Haut-parleur du téléphone uniquement", "Solo altoparlante del telefono", "휴대폰 스피커만", "仅手机扬声器", "携帯電話のスピーカーのみ",
            "Только динамик телефона", "مكبر صوت الهاتف فقط", "केवल फ़ोन स्पीकर", "Yalnızca telefon hoparlörü", "Tylko głośnik telefonu", "Hanya speaker ponsel", "Тільки динамік телефону", "Chỉ loa điện thoại"
        ),
        "ha_mobile_speaker_only_desc" to t(
            "Instellingen voor de externe speaker: geluid, volume en batterij-inschatting.", "Settings for the external speaker: sound, volume and battery estimate.", "Ajustes para el altavoz externo: sonido, volumen y estimación de batería.", "Configurações para o alto-falante externo: som, volume e estimativa de bateria.", "Einstellungen für den externen Lautsprecher: Ton, Lautstärke und Akkuschätzung.", "Paramètres du haut-parleur externe : son, volume et estimation de la batterie.", "Impostazioni per l'altoparlante esterno: suono, volume e stima della batteria.", "외부 스피커 설정: 소리, 볼륨 및 배터리 예측.", "外部扬声器设置：声音、音量和电池估算。", "外部スピーカーの設定：音、音量、バッテリー予測。",
            "Настройки внешнего динамика: звук, громкость и оценка заряда батареи.", "إعدادات مكبر الصوت الخارجي: الصوت ومستوى الصوت وتقدير البطارية.", "बाहरी स्पीकर के लिए सेटिंग्स: ध्वनि, वॉल्यूम और बैटरी अनुमान।", "Harici hoparlör ayarları: ses, ses seviyesi ve pil tahmini.", "Ustawienia zewnętrznego głośnika: dźwięk, głośność i szacowanie baterii.", "Pengaturan untuk speaker eksternal: suara, volume, dan perkiraan baterai.", "Налаштування зовнішнього динаміка: звук, гучність та оцінка заряду батареї.", "Cài đặt cho loa ngoài: âm thanh, âm lượng và ước tính pin."
        ),
        "ha_test_mobile_speaker_only" to t(
            "Test alleen mobiel speaker", "Test phone speaker only", "Probar solo altavoz del teléfono", "Testar apenas alto-falante do telefone", "Nur Telefonlautsprecher testen", "Tester uniquement le haut-parleur du téléphone", "Testa solo altoparlante del telefono", "휴대폰 스피커만 테스트", "仅测试手机扬声器", "携帯電話のスピーカーのみテスト",
            "Тест только динамика телефона", "اختبار مكبر صوت الهاتف فقط", "केवल फ़ोन स्पीकर का परीक्षण करें", "Yalnızca telefon hoparlörünü test et", "Testuj tylko głośnik telefonu", "Tes hanya speaker ponsel", "Тестувати лише динамік телефону", "Chỉ kiểm tra loa điện thoại"
        ),
        "ha_backup_speaker" to t(
            "Fallback speaker", "Fallback speaker", "Altavoz de respaldo", "Alto-falante de backup", "Fallback-Lautsprecher", "Haut-parleur de secours", "Altoparlante di backup", "대체 스피커", "备用扬声器", "フォールバックスピーカー",
            "Резервный динамик", "مكبر صوت احتياطي", "फ़ॉलबैक स्पीकर", "Yedek hoparlör", "Głośnik zapasowy", "Speaker cadangan", "Резервний динамік", "Loa dự phòng"
        ),
        "ha_backup_volume" to t(
            "Volume", "Volume", "Volumen", "Volume", "Lautstärke", "Volume", "Volume", "볼륨", "音量", "音量",
            "Громкость", "مستوى الصوت", "वॉल्यूम", "Ses seviyesi", "Głośność", "Volume", "Гучність", "Âm lượng"
        ),
        "ha_skip_volume" to t(
            "Volume laten staan", "Leave volume as is", "Dejar el volumen como está", "Deixar o volume como está", "Lautstärke unverändert lassen", "Laisser le volume tel quel", "Lascia il volume invariato", "볼륨 그대로 두기", "音量を変更しない", "音量を変更しない",
            "Оставить громкость без изменений", "اترك مستوى الصوت كما هو", "वॉल्यूम जैसा है वैसा रहने दें", "Ses seviyesini değiştirme", "Nie zmieniaj głośności", "Biarkan volume apa adanya", "Залишити гучність без змін", "Giữ nguyên âm lượng"
        ),
        "ha_skip_volume_desc" to t(
            "Zet geen volume op de externe speaker - die blijft gewoon staan waar hij al op stond.", "Don't set the external speaker's volume - it stays exactly where it already was.", "No ajustar el volumen del altavoz externo: se queda como estaba.", "Não ajustar o volume da coluna externa - permanece como estava.", "Stelt de Lautstärke des externen Lautsprechers nicht ein - bleibt wie sie war.", "Ne règle pas le volume de l'enceinte externe - il reste tel qu'il était.", "Non imposta il volume dell'altoparlante esterno - resta com'era.", "외부 스피커 볼륨을 설정하지 않습니다 - 이미 설정된 볼륨 그대로 유지됩니다.", "不设置外部扬声器音量 - 保持原样。", "外部スピーカーの音量は設定されません - そのまま維持されます。",
            "Не устанавливает громкость внешней колонки - остаётся как была.", "لا يتم ضبط مستوى صوت السماعة الخارجية - يبقى كما كان.", "बाहरी स्पीकर का वॉल्यूम सेट नहीं होगा - जैसा था वैसा ही रहेगा।", "Harici hoparlörün ses seviyesi ayarlanmaz - olduğu gibi kalır.", "Nie ustawia głośności zewnętrznego głośnika - zostaje taka, jaka była.", "Tidak mengatur volume speaker eksternal - tetap seperti semula.", "Не встановлює гучність зовнішньої колонки - залишається як була.", "Không đặt âm lượng loa ngoài - giữ nguyên như cũ."
        ),
        // Speaker-instellingen zijn sinds de speaker-splitsing apart per onderdeel (Agenda-alarm/
        // Timer/Weer) - deze melding verschijnt bij opslaan (mits er echt iets gewijzigd is) en
        // biedt aan dezelfde instellingen ook toe te passen op de andere onderdelen.
        "speaker_apply_other_title" to t(
            "Ook toepassen op andere onderdelen?", "Also apply to other parts?", "¿Aplicar también a otras partes?", "Aplicar também a outras partes?", "Auch auf andere Bereiche anwenden?", "Appliquer aussi à d'autres parties ?", "Applicare anche ad altre parti?", "다른 부분에도 적용할까요?", "也应用于其他部分？", "他の部分にも適用しますか？",
            "Также применить к другим разделам?", "هل تريد التطبيق أيضًا على أجزاء أخرى؟", "क्या अन्य हिस्सों पर भी लागू करें?", "Diğer bölümlere de uygulansın mı?", "Zastosować też do innych części?", "Terapkan juga ke bagian lain?", "Також застосувати до інших розділів?", "Áp dụng cho các phần khác không?"
        ),
        "speaker_apply_other_desc" to t(
            "Deze speaker-instellingen gelden nu alleen voor dit onderdeel. Vink hieronder aan waar je ze ook op wilt toepassen.", "These speaker settings now only apply to this part. Check below where else you'd like to apply them too.", "Estos ajustes de altavoz ahora solo se aplican a esta parte. Marca abajo dónde más quieres aplicarlos.", "Estas configurações de coluna aplicam-se agora apenas a esta parte. Marque abaixo onde mais deseja aplicá-las.", "Diese Lautsprecher-Einstellungen gelten jetzt nur für diesen Bereich. Wähle unten aus, wo sie sonst noch gelten sollen.", "Ces paramètres de haut-parleur ne s'appliquent désormais qu'à cette partie. Cochez ci-dessous où les appliquer aussi.", "Queste impostazioni dell'altoparlante ora si applicano solo a questa parte. Seleziona qui sotto dove applicarle anche.", "이 스피커 설정은 이제 이 부분에만 적용됩니다. 다른 곳에도 적용하려면 아래에서 선택하세요.", "这些扬声器设置现在仅适用于此部分。请在下方勾选还要应用到哪里。", "このスピーカー設定は今後この部分にのみ適用されます。他にも適用したい箇所を下でチェックしてください。",
            "Эти настройки динамика теперь применяются только к этому разделу. Отметьте ниже, где ещё их применить.", "تنطبق إعدادات مكبر الصوت هذه الآن على هذا الجزء فقط. حدد أدناه الأماكن الأخرى التي تريد تطبيقها عليها.", "ये स्पीकर सेटिंग्स अब केवल इस हिस्से पर लागू होती हैं। नीचे चुनें कि कहाँ और लागू करनी हैं।", "Bu hoparlör ayarları artık yalnızca bu bölüm için geçerli. Başka nerede uygulanacağını aşağıdan işaretle.", "Te ustawienia głośnika dotyczą teraz tylko tej części. Zaznacz poniżej, gdzie jeszcze mają obowiązywać.", "Pengaturan speaker ini kini hanya berlaku untuk bagian ini. Centang di bawah untuk menerapkannya juga di tempat lain.", "Ці налаштування динаміка тепер стосуються лише цього розділу. Позначте нижче, де ще їх застосувати.", "Các cài đặt loa này giờ chỉ áp dụng cho phần này. Đánh dấu bên dưới nếu muốn áp dụng ở nơi khác."
        ),
        // %s = naam van het andere onderdeel (Agenda-alarm/Timer/Weer, zie screen_agenda_alarm/
        // nav_timer/screen_weather)
        "speaker_apply_checkbox_format" to t(
            "Ook toepassen op %s", "Also apply to %s", "Aplicar también a %s", "Aplicar também a %s", "Auch auf %s anwenden", "Appliquer aussi à %s", "Applica anche a %s", "%s에도 적용", "也应用于%s", "%sにも適用",
            "Также применить к %s", "التطبيق أيضًا على %s", "%s पर भी लागू करें", "%s için de uygula", "Zastosuj też do %s", "Terapkan juga ke %s", "Також застосувати до %s", "Áp dụng cho %s"
        ),
        "ha_backup_sound" to t(
            "Geluid", "Sound", "Sonido", "Som", "Ton", "Son", "Suono", "소리", "声音", "サウンド",
            "Звук", "صوت", "ध्वनि", "Ses", "Dźwięk", "Suara", "Звук", "Âm thanh"
        ),
        "ha_backup_sound_default" to t(
            "Standaard alarmgeluid", "Default alarm sound", "Sonido de alarma predeterminado", "Som de alarme padrão", "Standard-Alarmton", "Son d'alarme par défaut", "Suono allarme predefinito", "기본 알람 소리", "默认闹钟声音", "デフォルトアラーム音",
            "Стандартный звук будильника", "صوت المنبه الافتراضي", "डिफ़ॉल्ट अलार्म ध्वनि", "Varsayılan alarm sesi", "Domyślny dźwięk alarmu", "Suara alarm default", "Стандартний звук будильника", "Âm thanh báo thức mặc định"
        ),
        "ha_backup_sound_custom" to t(
            "Aangepaste URL (.mp3)", "Custom URL (.mp3)", "URL personalizada (.mp3)", "URL personalizada (.mp3)", "Benutzerdefinierte URL (.mp3)", "URL personnalisée (.mp3)", "URL personalizzato (.mp3)", "사용자 정의 URL (.mp3)", "自定义 URL (.mp3)", "カスタムURL (.mp3)",
            "Пользовательский URL (.mp3)", "عنوان URL مخصص (.mp3)", "कस्टम URL (.mp3)", "Özel URL (.mp3)", "Niestandardowy URL (.mp3)", "URL khusus (.mp3)", "Власний URL (.mp3)", "URL tùy chỉnh (.mp3)"
        ),
        "ha_backup_sound_url_hint" to t(
            "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://ejemplo.com/alarma.mp3", "https://exemplo.com/alarme.mp3", "https://beispiel.de/alarm.mp3", "https://exemple.fr/alarme.mp3", "https://esempio.it/allarme.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3",
            "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3", "https://example.com/alarm.mp3"
        ),
        "ha_test_backup_alarm" to t(
            "Test backup alarm", "Test backup alarm", "Probar alarma de respaldo", "Testar alarme de backup", "Backup-Alarm testen", "Tester alarme de secours", "Testa allarme di backup", "백업 알람 테스트", "测试备用闹钟", "バックアップアラームをテスト",
            "Тест резервного будильника", "اختبار المنبه الاحتياطي", "बैकअप अलार्म का परीक्षण करें", "Yedek alarmı test et", "Testuj alarm zapasowy", "Tes alarm cadangan", "Тест резервного будильника", "Kiểm tra báo thức dự phòng"
        ),
        // Weer-specifieke test-knop: volgt de gekozen speaker/modus in SpeakerModal.kt. Deze
        // variant hoort bij modus "Standaard" (HA-speaker, met fallback naar telefoon als de
        // HA-speaker onbereikbaar is - zie WeatherAlertWorker.speakWeatherAlertIfEnabled). De
        // "Beide"-modus gebruikt ha_test_weather_speaker_both, "Mobiel speaker" gebruikt
        // ha_test_weather_speaker_phone hieronder. De \n plaatst het "(...)"-deel op een eigen
        // regel - zie de TextAlign.Center op de knoptekst in SpeakerModal.kt die dat centreert.
        "ha_test_weather_speaker" to t(
            "Test weermelding\n(HA-speaker)", "Test weather announcement\n(HA speaker)", "Probar aviso del tiempo\n(altavoz HA)", "Testar aviso do tempo\n(alto-falante HA)", "Wetteransage testen\n(HA-Lautsprecher)", "Tester l'annonce météo\n(haut-parleur HA)", "Testa l'annuncio meteo\n(altoparlante HA)", "날씨 안내 테스트\n(HA 스피커)", "测试天气播报\n（HA 扬声器）", "天気予報のテスト\n（HAスピーカー）",
            "Тест голосового прогноза\n(HA-динамик)", "اختبار إعلان الطقس\n(مكبر صوت HA)", "मौसम सूचना परीक्षण\n(HA स्पीकर)", "Hava durumu anonsunu test et\n(HA hoparlörü)", "Testuj zapowiedź pogody\n(głośnik HA)", "Uji pengumuman cuaca\n(speaker HA)", "Тест голосового прогнозу\n(HA-динамік)", "Kiểm tra thông báo thời tiết\n(loa HA)"
        ),
        "ha_test_weather_speaker_both" to t(
            "Test weermelding\n(HA-speaker + telefoon)", "Test weather announcement\n(HA speaker + phone)", "Probar aviso del tiempo\n(altavoz HA + teléfono)", "Testar aviso do tempo\n(alto-falante HA + telefone)", "Wetteransage testen\n(HA-Lautsprecher + Telefon)", "Tester l'annonce météo\n(haut-parleur HA + téléphone)", "Testa l'annuncio meteo\n(altoparlante HA + telefono)", "날씨 안내 테스트\n(HA 스피커 + 휴대폰)", "测试天气播报\n（HA 扬声器 + 手机）", "天気予報のテスト\n（HAスピーカー＋電話）",
            "Тест голосового прогноза\n(HA-динамик + телефон)", "اختبار إعلان الطقس\n(مكبر صوت HA + الهاتف)", "मौसम सूचना परीक्षण\n(HA स्पीकर + फ़ोन)", "Hava durumu anonsunu test et\n(HA hoparlörü + telefon)", "Testuj zapowiedź pogody\n(głośnik HA + telefon)", "Uji pengumuman cuaca\n(speaker HA + ponsel)", "Тест голосового прогнозу\n(HA-динамік + телефон)", "Kiểm tra thông báo thời tiết\n(loa HA + điện thoại)"
        ),
        "ha_test_weather_speaker_phone" to t(
            "Test weermelding\n(mobiel)", "Test weather announcement\n(phone)", "Probar aviso del tiempo\n(móvil)", "Testar aviso do tempo\n(celular)", "Wetteransage testen\n(Handy)", "Tester l'annonce météo\n(mobile)", "Testa l'annuncio meteo\n(cellulare)", "날씨 안내 테스트\n(휴대폰)", "测试天气播报\n（手机）", "天気予報のテスト\n（携帯電話）",
            "Тест голосового прогноза\n(мобильный)", "اختبار إعلان الطقس\n(الهاتف المحمول)", "मौसम सूचना परीक्षण\n(मोबाइल)", "Hava durumu anonsunu test et\n(mobil)", "Testuj zapowiedź pogody\n(telefon)", "Uji pengumuman cuaca\n(ponsel)", "Тест голосового прогнозу\n(мобільний)", "Kiểm tra thông báo thời tiết\n(di động)"
        ),
        // Gedeeld door telefoon- én HA-speaker-test (zie WeatherAlertWorker.testWeatherSpeechOnPhone/
        // testWeatherSpeechOnHaSpeaker) - vandaar geen apparaat-specifieke tekst, en zonder de eerder
        // aanwezige "als je dit hoort werkt het"-toevoeging: overbodig, je hoort het sowieso wel of niet.
        "weather_test_speech_message" to t(
            "Dit is een test van de weermelding.", "This is a test of the weather announcement.", "Esta es una prueba del aviso del tiempo.", "Este é um teste do aviso do tempo.", "Dies ist ein Test der Wetteransage.", "Ceci est un test de l'annonce météo.", "Questo è un test dell'annuncio meteo.", "날씨 안내 테스트입니다.", "这是天气播报测试。", "これは天気予報のテストです。",
            "Это тест голосового прогноза погоды.", "هذا اختبار لإعلان الطقس.", "यह मौसम सूचना का परीक्षण है।", "Bu, hava durumu anonsunun bir testidir.", "To jest test zapowiedzi pogody.", "Ini adalah uji pengumuman cuaca.", "Це тест голосового прогнозу погоди.", "Đây là bài kiểm tra thông báo thời tiết."
        ),
        "ha_backup_testing" to t(
            "Testen...", "Testing...", "Probando...", "Testando...", "Testen...", "Test en cours...", "Test in corso...", "테스트 중...", "测试中...", "テスト中...",
            "Тестирование...", "جاري الاختبار...", "परीक्षण हो रहा है...", "Test ediliyor...", "Testowanie...", "Menguji...", "Тестування...", "Đang kiểm tra..."
        ),
        "ha_backup_test_success" to t(
            "Test succesvol! Speaker speelt geluid af.", "Test successful! Speaker is playing sound.", "¡Prueba exitosa! El altavoz está reproduciendo sonido.", "Teste bem-sucedido! O alto-falante está tocando som.", "Test erfolgreich! Lautsprecher spielt Ton ab.", "Test réussi! Le haut-parleur joue le son.", "Test riuscito! L'altoparlante sta riproducendo il suono.", "테스트 성공! 스피커가 소리를 재생하고 있습니다.", "测试成功！扬声器正在播放声音。", "テスト成功！スピーカーが音を再生しています。",
            "Тест успешен! Динамик воспроизводит звук.", "نجح الاختبار! مكبر الصوت يشغل الصوت.", "परीक्षण सफल! स्पीकर ध्वनि बजा रहा है।", "Test başarılı! Hoparlör ses çalıyor.", "Test pomyślny! Głośnik odtwarza dźwięk.", "Tes berhasil! Speaker memutar suara.", "Тест успішний! Динамік відтворює звук.", "Kiểm tra thành công! Loa đang phát âm thanh."
        ),
        "ha_backup_no_speaker" to t(
            "Selecteer eerst een speaker", "Select a speaker first", "Seleccione un altavoz primero", "Selecione um alto-falante primeiro", "Wählen Sie zuerst einen Lautsprecher", "Sélectionnez d'abord un haut-parleur", "Seleziona prima un altoparlante", "먼저 스피커를 선택하세요", "请先选择扬声器", "まずスピーカーを選択してください",
            "Сначала выберите динамик", "حدد مكبر صوت أولاً", "पहले एक स्पीकर चुनें", "Önce bir hoparlör seçin", "Najpierw wybierz głośnik", "Pilih speaker terlebih dahulu", "Спочатку виберіть динамік", "Vui lòng chọn loa trước"
        ),
        "ha_backup_volume_disclaimer" to t(
            "Volume wordt toegepast indien ondersteund door de speaker", "Volume is applied if supported by the speaker", "El volumen se aplica si es compatible con el altavoz", "O volume é aplicado se suportado pelo alto-falante", "Lautstärke wird angewendet, wenn vom Lautsprecher unterstützt", "Le volume est appliqué si pris en charge par le haut-parleur", "Il volume viene applicato se supportato dall'altoparlante", "스피커에서 지원하는 경우 볼륨이 적용됩니다", "如果扬声器支持，则应用音量", "スピーカーがサポートしている場合、音量が適用されます",
            "Громкость применяется, если поддерживается динамиком", "يتم تطبيق مستوى الصوت إذا كان مدعومًا من مكبر الصوت", "यदि स्पीकर द्वारा समर्थित हो तो वॉल्यूम लागू होता है", "Hoparlör tarafından destekleniyorsa ses seviyesi uygulanır", "Głośność jest stosowana, jeśli jest obsługiwana przez głośnik", "Volume diterapkan jika didukung oleh speaker", "Гучність застосовується, якщо підтримується динаміком", "Âm lượng được áp dụng nếu được loa hỗ trợ"
        ),
        "ha_backup_no_speaker_configured" to t(
            "Geen externe speaker geconfigureerd. Stel eerst een externe speaker in bij de hoofdinstellingen.", "No external speaker configured. First set up an external speaker in the main settings.", "No hay altavoz externo configurado. Primero configure un altavoz externo en la configuración principal.", "Nenhum alto-falante externo configurado. Primeiro configure um alto-falante externo nas configurações principais.", "Kein externer Lautsprecher konfiguriert. Richten Sie zuerst einen externen Lautsprecher in den Haupteinstellungen ein.", "Aucun haut-parleur externe configuré. Configurez d'abord un haut-parleur externe dans les paramètres principaux.", "Nessun altoparlante esterno configurato. Prima configura un altoparlante esterno nelle impostazioni principali.", "외부 스피커가 구성되지 않았습니다. 먼저 기본 설정에서 외부 스피커를 설정하세요.", "未配置外部扬声器。请先在主设置中设置外部扬声器。", "外部スピーカーが設定されていません。まずメイン設定で外部スピーカーを設定してください。",
            "Внешний динамик не настроен. Сначала настройте внешний динамик в основных настройках.", "لم يتم تكوين مكبر صوت خارجي. قم أولاً بإعداد مكبر صوت خارجي في الإعدادات الرئيسية.", "कोई बाहरी स्पीकर कॉन्फ़िगर नहीं किया गया। पहले मुख्य सेटिंग्स में एक बाहरी स्पीकर सेट करें।", "Harici hoparlör yapılandırılmadı. Önce ana ayarlarda harici bir hoparlör kurun.", "Nie skonfigurowano zewnętrznego głośnika. Najpierw skonfiguruj zewnętrzny głośnik w głównych ustawieniach.", "Tidak ada speaker eksternal yang dikonfigurasi. Pertama atur speaker eksternal di pengaturan utama.", "Зовнішній динамік не налаштовано. Спочатку налаштуйте зовнішній динамік у головних налаштуваннях.", "Chưa cấu hình loa ngoài. Trước tiên hãy thiết lập loa ngoài trong cài đặt chính."
        ),
        "ha_backup_alarm_duration" to t(
            "Alarm duur", "Alarm duration", "Duración de alarma", "Duração do alarme", "Alarmdauer", "Durée de l'alarme", "Durata allarme", "알람 지속 시간", "闹钟持续时间", "アラーム持続時間",
            "Длительность будильника", "مدة المنبه", "अलार्म अवधि", "Alarm süresi", "Czas trwania alarmu", "Durasi alarm", "Тривалість будильника", "Thời lượng báo thức"
        ),
        "ha_backup_alarm_duration_desc" to t(
            "Hoe lang het alarm afspeelt op de externe speaker", "How long the alarm plays on the external speaker", "Cuánto tiempo suena la alarma en el altavoz externo", "Por quanto tempo o alarme toca no alto-falante externo", "Wie lange der Alarm auf dem externen Lautsprecher abgespielt wird", "Combien de temps l'alarme joue sur le haut-parleur externe", "Per quanto tempo l'allarme suona sull'altoparlante esterno", "외부 스피커에서 알람이 재생되는 시간", "闹钟在外部扬声器上播放多长时间", "外部スピーカーでアラームが再生される時間",
            "Как долго будильник воспроизводится на внешнем динамике", "كم من الوقت يعمل المنبه على مكبر الصوت الخارجي", "बाहरी स्पीकर पर अलार्म कितनी देर तक बजता है", "Harici hoparlörde alarm ne kadar süre çalar", "Jak długo alarm gra na zewnętrznym głośniku", "Berapa lama alarm berbunyi di speaker eksternal", "Як довго будильник відтворюється на зовнішньому динаміку", "Thời gian báo thức phát trên loa ngoài"
        ),
        "ha_backup_duration_minutes" to t(
            "Minuten", "Minutes", "Minutos", "Minutos", "Minuten", "Minutes", "Minuti", "분", "分钟", "分",
            "Минуты", "دقائق", "मिनट", "Dakika", "Minuty", "Menit", "Хвилини", "Phút"
        ),
        "ha_backup_duration_seconds" to t(
            "Seconden", "Seconds", "Segundos", "Segundos", "Sekunden", "Secondes", "Secondi", "초", "秒", "秒",
            "Секунды", "ثواني", "सेकंड", "Saniye", "Sekundy", "Detik", "Секунди", "Giây"
        ),

        // ── Weer-waarschuwing-teksten (gestructureerd, zie WeatherReason.kt) ──────────────────
        "weather_prob_high" to t(
            "Kans op {noun}", "Chance of {noun}", "Posibilidad de {noun}", "Chance de {noun}", "Chance auf {noun}", "Risque de {noun}", "Possibilità di {noun}", "{noun} 가능성", "有{noun}的可能", "{noun}の可能性",
            "Вероятность: {noun}", "احتمال {noun}", "{noun} की संभावना", "{noun} ihtimali", "Szansa na {noun}", "Kemungkinan {noun}", "Ймовірність: {noun}", "Khả năng có {noun}"
        ),
        "weather_prob_low" to t(
            "Kleine kans op {noun}", "Small chance of {noun}", "Pequeña posibilidad de {noun}", "Pequena chance de {noun}", "Geringe Chance auf {noun}", "Faible risque de {noun}", "Piccola possibilità di {noun}", "{noun} 가능성 낮음", "有小概率{noun}", "{noun}のわずかな可能性",
            "Небольшая вероятность: {noun}", "احتمال ضئيل لـ {noun}", "{noun} की थोड़ी संभावना", "Az bir {noun} ihtimali", "Mała szansa na {noun}", "Sedikit kemungkinan {noun}", "Невелика ймовірність: {noun}", "Khả năng nhỏ có {noun}"
        ),
        "weather_prob_none" to t(
            "Geen kans op {noun}", "No chance of {noun}", "Sin posibilidad de {noun}", "Sem chance de {noun}", "Keine Chance auf {noun}", "Aucun risque de {noun}", "Nessuna possibilità di {noun}", "{noun} 가능성 없음", "没有{noun}的可能", "{noun}の可能性なし",
            "Нет вероятности: {noun}", "لا احتمال لـ {noun}", "{noun} की कोई संभावना नहीं", "{noun} ihtimali yok", "Brak szans na {noun}", "Tidak ada kemungkinan {noun}", "Немає ймовірності: {noun}", "Không có khả năng {noun}"
        ),
        "weather_chance_high" to t(
            "Kans", "Chance", "Posibilidad", "Chance", "Chance", "Risque", "Possibilità", "가능성", "可能", "可能性あり",
            "Вероятно", "احتمال", "संभावना", "İhtimal", "Szansa", "Kemungkinan", "Ймовірно", "Khả năng"
        ),
        "weather_chance_low" to t(
            "Kleine kans", "Small chance", "Pequeña posibilidad", "Pequena chance", "Geringe Chance", "Faible risque", "Piccola possibilità", "가능성 낮음", "小概率", "わずかな可能性",
            "Небольшая вероятность", "احتمال ضئيل", "थोड़ी संभावना", "Az ihtimal", "Mała szansa", "Sedikit kemungkinan", "Невелика ймовірність", "Khả năng nhỏ"
        ),
        "weather_chance_none" to t(
            "Geen kans", "No chance", "Sin posibilidad", "Sem chance", "Keine Chance", "Aucun risque", "Nessuna possibilità", "가능성 없음", "无可能", "可能性なし",
            "Нет вероятности", "لا احتمال", "कोई संभावना नहीं", "İhtimal yok", "Brak szans", "Tidak ada kemungkinan", "Немає ймовірності", "Không có khả năng"
        ),
        "weather_chance_percent" to t(
            "{percent}% kans", "{percent}% chance", "{percent}% de posibilidad", "{percent}% de chance", "{percent}% Chance", "{percent}% de risque", "{percent}% di possibilità", "{percent}% 확률", "{percent}%的概率", "{percent}%の可能性",
            "Вероятность {percent}%", "احتمال {percent}%", "{percent}% संभावना", "%{percent} ihtimal", "{percent}% szans", "{percent}% kemungkinan", "Ймовірність {percent}%", "{percent}% khả năng"
        ),

        // Tijdvak bij een weer-waarschuwing (zie WeatherReason.periodText). {text} is de
        // waarschuwing zelf, {period} het tijdvak - in talen waar de tijd vooraan hoort, staat de
        // volgorde hieronder omgedraaid.
        "weather_with_period" to t(
            "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{period} {text}", "{period}{text}", "{period}{text}",
            "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}", "{text} {period}"
        ),
        "weather_period_range" to t(
            "van {from} tot {to}", "from {from} to {to}", "de {from} a {to}", "das {from} às {to}", "von {from} bis {to}", "de {from} à {to}", "dalle {from} alle {to}", "{from}~{to}", "{from}至{to}", "{from}〜{to}",
            "с {from} до {to}", "من {from} إلى {to}", "{from} से {to} तक", "{from} - {to} arası", "od {from} do {to}", "dari {from} sampai {to}", "з {from} до {to}", "từ {from} đến {to}"
        ),
        "weather_period_until" to t(
            "tot {to}", "until {to}", "hasta {to}", "até {to}", "bis {to}", "jusqu'à {to}", "fino alle {to}", "{to}까지", "至{to}", "{to}まで",
            "до {to}", "حتى {to}", "{to} तक", "{to}'a kadar", "do {to}", "sampai {to}", "до {to}", "đến {to}"
        ),
        "weather_period_all_day" to t(
            "de hele dag", "all day", "todo el día", "o dia todo", "den ganzen Tag", "toute la journée", "tutto il giorno", "하루 종일", "全天", "一日中",
            "весь день", "طوال اليوم", "पूरे दिन", "gün boyu", "przez cały dzień", "sepanjang hari", "весь день", "cả ngày"
        ),
        "weather_period_later_again" to t(
            "{period} en later opnieuw", "{period} and again later", "{period} y de nuevo más tarde", "{period} e novamente mais tarde", "{period} und später erneut", "{period} et de nouveau plus tard", "{period} e di nuovo più tardi", "{period}, 이후에 다시", "{period}，稍后再次", "{period}、その後再び",
            "{period} и снова позже", "{period} ومرة أخرى لاحقًا", "{period} और बाद में फिर", "{period} ve daha sonra tekrar", "{period} i ponownie później", "{period} dan lagi nanti", "{period} і знову пізніше", "{period} và lại sau đó"
        ),

        "weather_statement_thunder" to t(
            "onweer", "thunder", "tormenta eléctrica", "trovoada", "Gewitter", "orage", "temporale", "천둥번개", "雷暴", "雷雨",
            "гроза", "عاصفة رعدية", "गरज-तूफान", "gök gürültülü fırtına", "burza", "badai petir", "гроза", "giông bão"
        ),
        "weather_noun_thunder" to t(
            "onweer", "thunder", "tormenta eléctrica", "trovoada", "Gewitter", "orage", "temporale", "천둥번개", "雷暴", "雷雨",
            "грозу", "عاصفة رعدية", "गरज-तूफान", "gök gürültülü fırtına", "burzę", "badai petir", "грозу", "giông bão"
        ),
        "weather_statement_hail" to t(
            "hagel", "hail", "granizo", "granizo", "Hagel", "grêle", "grandine", "우박", "冰雹", "雹",
            "град", "برد", "ओलावृष्टि", "dolu", "grad", "hujan es", "град", "mưa đá"
        ),
        "weather_noun_hail" to t(
            "hagel", "hail", "granizo", "granizo", "Hagel", "grêle", "grandine", "우박", "冰雹", "雹",
            "град", "برد", "ओलावृष्टि", "dolu", "grad", "hujan es", "град", "mưa đá"
        ),
        "weather_statement_ice_road" to t(
            "gladde weg", "icy road", "carretera helada", "estrada gelada", "glatte Straße", "route verglacée", "strada ghiacciata", "도로 결빙", "路面结冰", "路面凍結",
            "гололёд на дороге", "طريق مثلج", "फिसलन भरी सड़क", "buzlu yol", "śliska droga", "jalan licin", "ожеледиця на дорозі", "đường trơn trượt"
        ),
        "weather_noun_ice_road" to t(
            "gladheid", "icy roads", "hielo en la carretera", "gelo na estrada", "Glätte", "verglas", "ghiaccio sulla strada", "도로 결빙", "路面结冰", "路面凍結",
            "гололёд", "جليد على الطريق", "फिसलन", "buzlanma", "śliskość", "jalan licin", "ожеледиця", "trơn trượt"
        ),
        "weather_statement_wet_snow" to t(
            "natte sneeuw", "wet snow", "nieve húmeda", "neve molhada", "nasser Schnee", "neige mouillée", "neve bagnata", "습설", "湿雪", "みぞれ雪",
            "мокрый снег", "ثلج رطب", "गीली बर्फ", "sulu kar", "mokry śnieg", "salju basah", "мокрий сніг", "tuyết ướt"
        ),
        "weather_noun_wet_snow" to t(
            "natte sneeuw", "wet snow", "nieve húmeda", "neve molhada", "nasser Schnee", "neige mouillée", "neve bagnata", "습설", "湿雪", "みぞれ雪",
            "мокрый снег", "ثلج رطب", "गीली बर्फ", "sulu kar", "mokry śnieg", "salju basah", "мокрий сніг", "tuyết ướt"
        ),

        "weather_statement_drizzle_light" to t(
            "het motregent zacht", "it's drizzling lightly", "llovizna ligera", "chuvisco leve", "es nieselt leicht", "il bruine légèrement", "pioviggina leggermente", "약한 이슬비가 내림", "小毛毛雨", "小雨がぱらつく",
            "слегка моросит", "رذاذ خفيف", "हल्की बूंदाबांदी हो रही है", "hafif çiseliyor", "lekko mży", "gerimis ringan", "легка мряка", "mưa phùn nhẹ"
        ),
        "weather_noun_drizzle_light" to t(
            "lichte motregen", "light drizzle", "llovizna ligera", "chuvisco leve", "leichter Nieselregen", "bruine légère", "pioviggine leggera", "약한 이슬비", "小毛毛雨", "小雨",
            "лёгкую морось", "رذاذ خفيف", "हल्की बूंदाबांदी", "hafif çisenti", "lekką mżawkę", "gerimis ringan", "легку мряку", "mưa phùn nhẹ"
        ),
        "weather_statement_drizzle_normal" to t(
            "het motregent", "it's drizzling", "está lloviznando", "está chuviscando", "es nieselt", "il bruine", "pioviggina", "이슬비가 내림", "下毛毛雨", "霧雨が降る",
            "моросит", "رذاذ", "बूंदाबांदी हो रही है", "çiseliyor", "mży", "gerimis", "мрячить", "mưa phùn"
        ),
        "weather_noun_drizzle_normal" to t(
            "motregen", "drizzle", "llovizna", "chuvisco", "Nieselregen", "bruine", "pioviggine", "이슬비", "毛毛雨", "霧雨",
            "морось", "رذاذ", "बूंदाबांदी", "çisenti", "mżawkę", "gerimis", "мряку", "mưa phùn"
        ),
        "weather_statement_drizzle_heavy" to t(
            "het motregent flink", "it's drizzling heavily", "llovizna intensa", "chuvisco forte", "es nieselt stark", "il bruine fortement", "pioviggina forte", "강한 이슬비가 내림", "毛毛雨较大", "強い霧雨が降る",
            "сильно моросит", "رذاذ غزير", "तेज़ बूंदाबांदी हो रही है", "yoğun çiseliyor", "mocno mży", "gerimis lebat", "сильно мрячить", "mưa phùn nặng hạt"
        ),
        "weather_noun_drizzle_heavy" to t(
            "flinke motregen", "heavy drizzle", "llovizna intensa", "chuvisco forte", "starker Nieselregen", "forte bruine", "pioviggine forte", "강한 이슬비", "较大毛毛雨", "強い霧雨",
            "сильную морось", "رذاذ غزير", "तेज़ बूंदाबांदी", "yoğun çisenti", "mocną mżawkę", "gerimis lebat", "сильну мряку", "mưa phùn nặng hạt"
        ),
        "weather_statement_freezing" to t(
            "het ijzelt", "it's icy", "hay hielo", "está com gelo", "es herrscht Glatteis", "il y a du verglas", "c'è ghiaccio", "빙판길이 있음", "有冻雨", "路面凍結あり",
            "гололедица", "هناك جليد", "बर्फ़ जमी है", "buzlanma var", "jest gołoledź", "ada es", "ожеледиця", "có băng giá"
        ),
        "weather_noun_freezing" to t(
            "ijzel", "ice", "hielo", "gelo", "Glatteis", "verglas", "ghiaccio", "빙판", "冻雨", "凍結",
            "гололедицу", "جليد", "बर्फ़", "buzlanma", "gołoledź", "es", "ожеледицю", "băng giá"
        ),
        "weather_statement_rain_light" to t(
            "het regent zacht", "it's raining lightly", "llueve ligero", "chove fraco", "es regnet leicht", "il pleut légèrement", "piove leggermente", "약한 비가 내림", "小雨", "小雨が降る",
            "идёт небольшой дождь", "أمطار خفيفة", "हल्की बारिश हो रही है", "hafif yağmur yağıyor", "lekko pada", "hujan ringan", "йде легкий дощ", "mưa nhẏ"
        ),
        "weather_noun_rain_light" to t(
            "lichte regen", "light rain", "lluvia ligera", "chuva fraca", "leichter Regen", "pluie légère", "pioggia leggera", "약한 비", "小雨", "小雨",
            "лёгкий дождь", "أمطار خفيفة", "हल्की बारिश", "hafif yağmur", "lekki deszcz", "hujan ringan", "легкий дощ", "mưa nhẹ"
        ),
        "weather_statement_rain_normal" to t(
            "het regent", "it's raining", "está lloviendo", "está chovendo", "es regnet", "il pleut", "piove", "비가 내림", "下雨", "雨が降る",
            "идёт дождь", "أمطار", "बारिश हो रही है", "yağmur yağıyor", "pada deszcz", "hujan", "йде дощ", "trời mưa"
        ),
        "weather_noun_rain_normal" to t(
            "regen", "rain", "lluvia", "chuva", "Regen", "pluie", "pioggia", "비", "雨", "雨",
            "дождь", "أمطار", "बारिश", "yağmur", "deszcz", "hujan", "дощ", "mưa"
        ),
        "weather_statement_rain_heavy" to t(
            "het regent hard", "it's raining heavily", "llueve fuerte", "chove forte", "es regnet stark", "il pleut fort", "piove forte", "강한 비가 내림", "大雨", "強い雨が降る",
            "идёт сильный дождь", "أمطار غزيرة", "तेज़ बारिश हो रही है", "şiddetli yağmur yağıyor", "mocno pada", "hujan lebat", "йде сильний дощ", "mưa lớn"
        ),
        "weather_noun_rain_heavy" to t(
            "zware regen", "heavy rain", "lluvia fuerte", "chuva forte", "starker Regen", "forte pluie", "pioggia forte", "강한 비", "大雨", "強い雨",
            "сильный дождь", "أمطار غزيرة", "तेज़ बारिश", "şiddetli yağmur", "silny deszcz", "hujan lebat", "сильний дощ", "mưa lớn"
        ),
        "weather_statement_snow_light" to t(
            "het sneeuwt zacht", "it's snowing lightly", "nieva ligero", "neva fraco", "es schneit leicht", "il neige légèrement", "nevica leggermente", "약한 눈이 내림", "小雪", "小雪が降る",
            "идёт небольшой снег", "ثلوج خفيفة", "हल्की बर्फ़बारी हो रही है", "hafif kar yağıyor", "lekko śnieży", "salju ringan", "йде легкий сніг", "tuyết rơi nhẹ"
        ),
        "weather_noun_snow_light" to t(
            "lichte sneeuw", "light snow", "nieve ligera", "neve fraca", "leichter Schnee", "neige légère", "neve leggera", "약한 눈", "小雪", "小雪",
            "лёгкий снег", "ثلوج خفيفة", "हल्की बर्फ़बारी", "hafif kar", "lekki śnieg", "salju ringan", "легкий сніг", "tuyết nhẹ"
        ),
        "weather_statement_snow_normal" to t(
            "het sneeuwt", "it's snowing", "está nevando", "está nevando", "es schneit", "il neige", "nevica", "눈이 내림", "下雪", "雪が降る",
            "идёт снег", "ثلوج", "बर्फ़बारी हो रही है", "kar yağıyor", "pada śnieg", "salju turun", "йде сніг", "trời có tuyết"
        ),
        "weather_noun_snow_normal" to t(
            "sneeuw", "snow", "nieve", "neve", "Schnee", "neige", "neve", "눈", "雪", "雪",
            "снег", "ثلوج", "बर्फ़बारी", "kar", "śnieg", "salju", "сніг", "tuyết"
        ),
        "weather_statement_snow_heavy" to t(
            "het sneeuwt hard", "it's snowing heavily", "nieva fuerte", "neva forte", "es schneit stark", "il neige fort", "nevica intensamente", "강한 눈이 내림", "大雪", "強い雪が降る",
            "идёт сильный снег", "ثلوج غزيرة", "भारी बर्फ़बारी हो रही है", "yoğun kar yağıyor", "mocno śnieży", "salju lebat", "йде сильний сніг", "tuyết rơi dày"
        ),
        "weather_noun_snow_heavy" to t(
            "zware sneeuwval", "heavy snow", "nevada fuerte", "nevasca forte", "starker Schneefall", "fortes chutes de neige", "forte nevicata", "강한 눈", "大雪", "大雪",
            "сильный снегопад", "تساقط ثلوج غزيرة", "भारी बर्फ़बारी", "yoğun kar yağışı", "silny opad śniegu", "salju lebat", "сильний снігопад", "tuyết rơi dày"
        ),
        "weather_statement_snow_grains" to t(
            "sneeuwkorrels", "snow grains", "granos de nieve", "grãos de neve", "Schneegriesel", "grésil", "nevischio", "싸락눈", "米雪", "細氷",
            "снежная крупа", "حبيبات ثلجية", "बर्फ़ के कण", "kar taneleri", "śnieg ziarnisty", "butiran salju", "снігова крупа", "hạt tuyết"
        ),
        "weather_noun_snow_grains" to t(
            "sneeuwkorrels", "snow grains", "granos de nieve", "grãos de neve", "Schneegriesel", "grésil", "nevischio", "싸락눈", "米雪", "細氷",
            "снежную крупу", "حبيبات ثلجية", "बर्फ़ के कण", "kar taneleri", "śnieg ziarnisty", "butiran salju", "снігову крупу", "hạt tuyết"
        ),
        "weather_statement_rain_showers_light" to t(
            "lichte regenbuien", "light rain showers", "chubascos ligeros", "aguaceiros fracos", "leichte Regenschauer", "légères averses", "rovesci leggeri", "약한 소나기", "小阵雨", "弱いにわか雨",
            "небольшие ливни", "زخات مطر خفيفة", "हल्की बौछारें", "hafif sağanak yağmur", "lekkie przelotne opady", "hujan ringan sebentar", "невеликі зливи", "mưa rào nhẹ"
        ),
        "weather_noun_rain_showers_light" to t(
            "lichte regenbuien", "light rain showers", "chubascos ligeros", "aguaceiros fracos", "leichte Regenschauer", "légères averses", "rovesci leggeri", "약한 소나기", "小阵雨", "弱いにわか雨",
            "небольшие ливни", "زخات مطر خفيفة", "हल्की बौछारें", "hafif sağanak yağmur", "lekkie przelotne opady", "hujan ringan sebentar", "невеликі зливи", "mưa rào nhẹ"
        ),
        "weather_statement_rain_showers_normal" to t(
            "regenbuien", "rain showers", "chubascos", "aguaceiros", "Regenschauer", "averses", "rovesci", "소나기", "阵雨", "にわか雨",
            "ливни", "زخات مطر", "बौछारें", "sağanak yağmur", "przelotne opady deszczu", "hujan sebentar", "зливи", "mưa rào"
        ),
        "weather_noun_rain_showers_normal" to t(
            "regenbuien", "rain showers", "chubascos", "aguaceiros", "Regenschauer", "averses", "rovesci", "소나기", "阵雨", "にわか雨",
            "ливни", "زخات مطر", "बौछारें", "sağanak yağmur", "przelotne opady deszczu", "hujan sebentar", "зливи", "mưa rào"
        ),
        "weather_statement_rain_showers_heavy" to t(
            "hevige regenbuien", "heavy rain showers", "chubascos intensos", "aguaceiros fortes", "starke Regenschauer", "fortes averses", "rovesci intensi", "강한 소나기", "强阵雨", "激しいにわか雨",
            "сильные ливни", "زخات مطر غزيرة", "तेज़ बौछारें", "kuvvetli sağanak yağmur", "silne przelotne opady", "hujan lebat sebentar", "сильні зливи", "mưa rào lớn"
        ),
        "weather_noun_rain_showers_heavy" to t(
            "hevige regenbuien", "heavy rain showers", "chubascos intensos", "aguaceiros fortes", "starke Regenschauer", "fortes averses", "rovesci intensi", "강한 소나기", "强阵雨", "激しいにわか雨",
            "сильные ливни", "زخات مطر غزيرة", "तेज़ बौछारें", "kuvvetli sağanak yağmur", "silne przelotne opady", "hujan lebat sebentar", "сильні зливи", "mưa rào lớn"
        ),
        "weather_statement_snow_showers_light" to t(
            "lichte sneeuwbuien", "light snow showers", "nevadas ligeras", "aguaceiros de neve fracos", "leichte Schneeschauer", "légères averses de neige", "deboli rovesci di neve", "약한 눈 소나기", "小阵雪", "弱いにわか雪",
            "небольшие снегопады", "زخات ثلج خفيفة", "हल्की बर्फ़ की बौछारें", "hafif kar sağanağı", "lekkie przelotne opady śniegu", "hujan salju ringan sebentar", "невеликі снігопади", "mưa tuyết rào nhẹ"
        ),
        "weather_noun_snow_showers_light" to t(
            "lichte sneeuwbuien", "light snow showers", "nevadas ligeras", "aguaceiros de neve fracos", "leichte Schneeschauer", "légères averses de neige", "deboli rovesci di neve", "약한 눈 소나기", "小阵雪", "弱いにわか雪",
            "небольшие снегопады", "زخات ثلج خفيفة", "हल्की बर्फ़ की बौछारें", "hafif kar sağanağı", "lekkie przelotne opady śniegu", "hujan salju ringan sebentar", "невеликі снігопади", "mưa tuyết rào nhẹ"
        ),
        "weather_statement_snow_showers_heavy" to t(
            "hevige sneeuwbuien", "heavy snow showers", "nevadas intensas", "aguaceiros de neve fortes", "starke Schneeschauer", "fortes averses de neige", "forti rovesci di neve", "강한 눈 소나기", "强阵雪", "激しいにわか雪",
            "сильные снегопады", "زخات ثلج غزيرة", "तेज़ बर्फ़ की बौछारें", "kuvvetli kar sağanağı", "silne przelotne opady śniegu", "hujan salju lebat sebentar", "сильні снігопади", "mưa tuyết rào lớn"
        ),
        "weather_noun_snow_showers_heavy" to t(
            "hevige sneeuwbuien", "heavy snow showers", "nevadas intensas", "aguaceiros de neve fortes", "starke Schneeschauer", "fortes averses de neige", "forti rovesci di neve", "강한 눈 소나기", "强阵雪", "激しいにわか雪",
            "сильные снегопады", "زخات ثلج غزيرة", "तेज़ बर्फ़ की बौछारें", "kuvvetli kar sağanağı", "silne przelotne opady śniegu", "hujan salju lebat sebentar", "сильні снігопади", "mưa tuyết rào lớn"
        ),
        "weather_statement_mixed" to t(
            "wisselvallig weer", "changeable weather", "tiempo variable", "tempo instável", "wechselhaftes Wetter", "temps changeant", "tempo variabile", "변덕스러운 날씨", "多变天气", "変わりやすい天気",
            "переменчивая погода", "طقس متقلب", "बदलता मौसम", "değişken hava", "zmienna pogoda", "cuaca berubah-ubah", "мінлива погода", "thời tiết thất thường"
        ),
        "weather_noun_mixed" to t(
            "wisselvallig weer", "changeable weather", "tiempo variable", "tempo instável", "wechselhaftes Wetter", "temps changeant", "tempo variabile", "변덕스러운 날씨", "多变天气", "変わりやすい天気",
            "переменчивую погоду", "طقس متقلب", "बदलता मौसम", "değişken hava", "zmienną pogodę", "cuaca berubah-ubah", "мінливу погоду", "thời tiết thất thường"
        ),

        "weather_hurricane_gusts" to t(
            "Orkaanachtige windstoten tot {speed} km/u", "Hurricane-force gusts up to {speed} km/h", "Ráfagas huracanadas de hasta {speed} km/h", "Rajadas com força de furacão até {speed} km/h", "Orkanartige Böen bis zu {speed} km/h", "Rafales de force ouragan jusqu'à {speed} km/h", "Raffiche di uragano fino a {speed} km/h", "시속 {speed}km에 달하는 허리케인급 돌풍", "飓风级阵风高达{speed}公里/小时", "最大{speed}km/hのハリケーン級突風",
            "Ураганные порывы до {speed} км/ч", "هبات رياح إعصارية تصل إلى {speed} كم/س", "{speed} किमी/घंटा तक की तूफ़ानी हवाएँ", "{speed} km/sa'e varan kasırga şiddetinde rüzgar", "Porywy huraganowe do {speed} km/h", "Hembusan angin badai hingga {speed} km/jam", "Ураганні пориви до {speed} км/год", "Gió giật cấp bão lên tới {speed} km/h"
        ),
        "weather_hurricane_noun" to t(
            "orkaanachtige wind", "hurricane-force wind", "viento huracanado", "vento com força de furacão", "orkanartiger Wind", "vent de force ouragan", "vento di forza uragano", "허리케인급 강풍", "飓风级大风", "ハリケーン級の強風",
            "ураганный ветер", "رياح إعصارية", "तूफ़ानी हवा", "kasırga şiddetinde rüzgar", "wiatr huraganowy", "angin kencang badai", "ураганний вітер", "gió cấp bão"
        ),
        "weather_storm_gusts" to t(
            "Storm met windstoten tot {speed} km/u", "Storm with gusts up to {speed} km/h", "Tormenta con ráfagas de hasta {speed} km/h", "Tempestade com rajadas até {speed} km/h", "Sturm mit Böen bis zu {speed} km/h", "Tempête avec rafales jusqu'à {speed} km/h", "Tempesta con raffiche fino a {speed} km/h", "시속 {speed}km에 달하는 돌풍을 동반한 폭풍", "阵风高达{speed}公里/小时的暴风", "最大{speed}km/hの突風を伴う暴風",
            "Шторм с порывами до {speed} км/ч", "عاصفة بهبات رياح تصل إلى {speed} كم/س", "{speed} किमी/घंटा तक की तेज़ हवाओं वाला तूफ़ान", "{speed} km/sa'e varan rüzgarlı fırtına", "Sztorm z porywami do {speed} km/h", "Badai dengan hembusan angin hingga {speed} km/jam", "Шторм із поривами до {speed} км/год", "Bão với gió giật lên tới {speed} km/h"
        ),
        "weather_storm_noun" to t(
            "storm", "storm", "temporal", "tempestade", "Sturm", "tempête", "tempesta", "폭풍", "暴风", "暴風",
            "шторм", "عاصفة", "तूफ़ान", "fırtına", "sztorm", "badai", "шторм", "bão"
        ),
        "weather_heat_statement" to t(
            "Extreme hitte", "Extreme heat", "Calor extremo", "Calor extremo", "Extreme Hitze", "Chaleur extrême", "Caldo estremo", "폭염", "极端高温", "猛烈な暑さ",
            "Экстремальная жара", "حرارة شديدة", "अत्यधिक गर्मी", "Aşırı sıcaklık", "Ekstremalny upał", "Panas ekstrem", "Екстремальна спека", "Nắng nóng cực đoan"
        ),
        "weather_heat_noun" to t(
            "extreme hitte", "extreme heat", "calor extremo", "calor extremo", "extreme Hitze", "chaleur extrême", "caldo estremo", "폭염", "极端高温", "猛烈な暑さ",
            "экстремальную жару", "حرارة شديدة", "अत्यधिक गर्मी", "aşırı sıcaklık", "ekstremalny upał", "panas ekstrem", "екстремальну спеку", "nắng nóng cực đoan"
        ),
        "weather_tomorrow_prefix" to t(
            "Morgen", "Tomorrow", "Mañana", "Amanhã", "Morgen", "Demain", "Domani", "내일", "明天", "明日",
            "Завтра", "غدًا", "कल", "Yarın", "Jutro", "Besok", "Завтра", "Ngày mai"
        ),
        "weather_temp_change_more" to t(
            "{degrees} graden meer dan vandaag", "{degrees} degrees more than today", "{degrees} grados más que hoy", "{degrees} graus a mais que hoje", "{degrees} Grad mehr als heute", "{degrees} degrés de plus qu'aujourd'hui", "{degrees} gradi in più di oggi", "오늘보다 {degrees}도 높음", "比今天高{degrees}度", "今日より{degrees}度高い",
            "На {degrees} градусов теплее, чем сегодня", "{degrees} درجة أكثر من اليوم", "आज से {degrees} डिग्री ज़्यादा", "bugünden {degrees} derece daha fazla", "{degrees} stopni więcej niż dziś", "{degrees} derajat lebih tinggi dari hari ini", "На {degrees} градусів тепліше, ніж сьогодні", "cao hơn {degrees} độ so với hôm nay"
        ),
        "weather_temp_change_less" to t(
            "{degrees} graden minder dan vandaag", "{degrees} degrees less than today", "{degrees} grados menos que hoy", "{degrees} graus a menos que hoje", "{degrees} Grad weniger als heute", "{degrees} degrés de moins qu'aujourd'hui", "{degrees} gradi in meno di oggi", "오늘보다 {degrees}도 낮음", "比今天低{degrees}度", "今日より{degrees}度低い",
            "На {degrees} градусов холоднее, чем сегодня", "{degrees} درجة أقل من اليوم", "आज से {degrees} डिग्री कम", "bugünden {degrees} derece daha az", "{degrees} stopni mniej niż dziś", "{degrees} derajat lebih rendah dari hari ini", "На {degrees} градусів холодніше, ніж сьогодні", "thấp hơn {degrees} độ so với hôm nay"
        ),
        "weather_temp_change_title" to t(
            "Het wordt morgen ongeveer {temp} graden", "It'll be about {temp} degrees tomorrow", "Mañana hará unos {temp} grados", "Amanhã fará cerca de {temp} graus", "Morgen wird es etwa {temp} Grad", "Il fera environ {temp} degrés demain", "Domani ci saranno circa {temp} gradi", "내일은 약 {temp}도가 될 예정입니다", "明天大约{temp}度", "明日の気温は約{temp}度になります",
            "Завтра будет около {temp} градусов", "ستكون الحرارة غدًا حوالي {temp} درجة", "कल लगभग {temp} डिग्री तापमान रहेगा", "yarın yaklaşık {temp} derece olacak", "Jutro będzie około {temp} stopni", "Besok akan sekitar {temp} derajat", "Завтра буде близько {temp} градусів", "Ngày mai sẽ khoảng {temp} độ"
        ),
        // Zelfde zin als hierboven, maar voor de dag ná morgen. De "Dag ervoor"-melding voor
        // temperatuurwisselingen gaat namelijk over overmorgen (hij waarschuwt een dag eerder dan
        // de "Zelfde dag"-variant) en zei desondanks "morgen".
        "weather_temp_change_title_day_after" to t(
            "Het wordt overmorgen ongeveer {temp} graden", "It'll be about {temp} degrees the day after tomorrow", "Pasado mañana hará unos {temp} grados", "Depois de amanhã fará cerca de {temp} graus", "Übermorgen wird es etwa {temp} Grad", "Il fera environ {temp} degrés après-demain", "Dopodomani ci saranno circa {temp} gradi", "모레는 약 {temp}도가 될 예정입니다", "后天大约{temp}度", "明後日の気温は約{temp}度になります",
            "Послезавтра будет около {temp} градусов", "ستكون الحرارة بعد غد حوالي {temp} درجة", "परसों लगभग {temp} डिग्री तापमान रहेगा", "yarından sonraki gün yaklaşık {temp} derece olacak", "Pojutrze będzie około {temp} stopni", "Lusa akan sekitar {temp} derajat", "Післязавтра буде близько {temp} градусів", "Ngày kia sẽ khoảng {temp} độ"
        ),
        // Zonder dagaanduiding: voor meldingen rond een agenda-item, waar de dag al uit het
        // agenda-item zelf blijkt en een vaste "morgen" er domweg naast zat.
        "weather_temp_change_title_plain" to t(
            "Het wordt ongeveer {temp} graden", "It'll be about {temp} degrees", "Hará unos {temp} grados", "Fará cerca de {temp} graus", "Es wird etwa {temp} Grad", "Il fera environ {temp} degrés", "Ci saranno circa {temp} gradi", "약 {temp}도가 될 예정입니다", "大约{temp}度", "気温は約{temp}度になります",
            "Будет около {temp} градусов", "ستكون الحرارة حوالي {temp} درجة", "लगभग {temp} डिग्री तापमान रहेगा", "yaklaşık {temp} derece olacak", "Będzie około {temp} stopni", "Akan sekitar {temp} derajat", "Буде близько {temp} градусів", "Sẽ khoảng {temp} độ"
        ),
        // Als de vergelijkingsdag morgen is in plaats van vandaag (de "Dag ervoor"-melding
        // vergelijkt overmorgen mét morgen, niet met vandaag).
        "weather_temp_change_more_than_tomorrow" to t(
            "{degrees} graden meer dan morgen", "{degrees} degrees more than tomorrow", "{degrees} grados más que mañana", "{degrees} graus a mais que amanhã", "{degrees} Grad mehr als morgen", "{degrees} degrés de plus que demain", "{degrees} gradi in più di domani", "내일보다 {degrees}도 높음", "比明天高{degrees}度", "明日より{degrees}度高い",
            "На {degrees} градусов теплее, чем завтра", "{degrees} درجة أكثر من الغد", "कल से {degrees} डिग्री ज़्यादा", "yarından {degrees} derece daha fazla", "{degrees} stopni więcej niż jutro", "{degrees} derajat lebih tinggi dari besok", "На {degrees} градусів тепліше, ніж завтра", "cao hơn {degrees} độ so với ngày mai"
        ),
        "weather_temp_change_less_than_tomorrow" to t(
            "{degrees} graden minder dan morgen", "{degrees} degrees less than tomorrow", "{degrees} grados menos que mañana", "{degrees} graus a menos que amanhã", "{degrees} Grad weniger als morgen", "{degrees} degrés de moins que demain", "{degrees} gradi in meno di domani", "내일보다 {degrees}도 낮음", "比明天低{degrees}度", "明日より{degrees}度低い",
            "На {degrees} градусов холоднее, чем завтра", "{degrees} درجة أقل من الغد", "कल से {degrees} डिग्री कम", "yarından {degrees} derece daha az", "{degrees} stopni mniej niż jutro", "{degrees} derajat lebih rendah dari besok", "На {degrees} градусів холодніше, ніж завтра", "thấp hơn {degrees} độ so với ngày mai"
        ),
        "weather_notification_title" to t(
            "Weerwaarschuwing", "Weather warning", "Aviso meteorológico", "Aviso meteorológico", "Wetterwarnung", "Alerte météo", "Avviso meteo", "기상 경보", "天气预警", "気象警報",
            "Погодное предупреждение", "تحذير جوي", "मौसम चेतावनी", "Hava durumu uyarısı", "Ostrzeżenie pogodowe", "Peringatan cuaca", "Погодне попередження", "Cảnh báo thời tiết"
        ),
        "weather_channel_name" to t(
            "Weermeldingen", "Weather notifications", "Notificaciones meteorológicas", "Notificações meteorológicas", "Wettermeldungen", "Notifications météo", "Notifiche meteo", "날씨 알림", "天气通知", "気象通知",
            "Погодные уведомления", "إشعارات الطقس", "मौसम सूचनाएं", "Hava durumu bildirimleri", "Powiadomienia pogodowe", "Notifikasi cuaca", "Погодні сповіщення", "Thông báo thời tiết"
        ),
        "weather_channel_description" to t(
            "Waarschuwingen voor slecht weer en grote temperatuurwisselingen", "Warnings for bad weather and large temperature changes", "Avisos de mal tiempo y grandes cambios de temperatura", "Avisos de mau tempo e grandes mudanças de temperatura", "Warnungen vor schlechtem Wetter und großen Temperaturschwankungen", "Alertes de mauvais temps et de grands changements de température", "Avvisi per maltempo e grandi variazioni di temperatura", "악천후 및 큰 기온 변화 경보", "恶劣天气和大幅温度变化预警", "悪天候および大幅な気温変化の警告",
            "Предупреждения о плохой погоде и резких перепадах температуры", "تحذيرات من الطقس السيئ والتغيرات الكبيرة في درجة الحرارة", "खराब मौसम और बड़े तापमान परिवर्तन की चेतावनियां", "Kötü hava koşulları ve büyük sıcaklık değişimleri için uyarılar", "Ostrzeżenia o złej pogodzie i dużych zmianach temperatury", "Peringatan cuaca buruk dan perubahan suhu besar", "Попередження про погану погоду та значні перепади температури", "Cảnh báo thời tiết xấu và thay đổi nhiệt độ lớn"
        ),
        "weather_temp_change_expected" to t(
            "groot temperatuurverschil verwacht", "large temperature change expected", "se espera un gran cambio de temperatura", "grande mudança de temperatura esperada", "großer Temperaturunterschied erwartet", "grand changement de température attendu", "previsto un grande sbalzo termico", "큰 기온 변화 예상", "预计温差较大", "大きな気温変化が予想されます",
            "ожидается большой перепад температуры", "من المتوقع فرق كبير في درجة الحرارة", "बड़ा तापमान परिवर्तन अपेक्षित", "büyük sıcaklık farkı bekleniyor", "spodziewana duża zmiana temperatury", "diperkirakan perubahan suhu besar", "очікується великий перепад температури", "dự kiến thay đổi nhiệt độ lớn"
        ),
        "weather_no_warning" to t(
            "geen waarschuwing", "no warning", "sin avisos", "sem avisos", "keine Warnung", "aucune alerte", "nessun avviso", "경보 없음", "无预警", "警告なし",
            "нет предупреждений", "لا توجد تحذيرات", "कोई चेतावनी नहीं", "uyarı yok", "brak ostrzeżeń", "tidak ada peringatan", "немає попереджень", "không có cảnh báo"
        ),
        "weather_calm_default" to t(
            "rustig weer voor je komende afspraken", "calm weather for your upcoming appointments", "tiempo tranquilo para tus próximas citas", "tempo calmo para os seus próximos compromissos", "ruhiges Wetter für deine anstehenden Termine", "temps calme pour vos prochains rendez-vous", "tempo tranquillo per i tuoi prossimi impegni", "다가오는 일정에 좋은 날씨", "接下来的日程天气平静", "今後の予定に向けて穏やかな天気です",
            "спокойная погода для ваших предстоящих встреч", "طقس هادئ لمواعيدك القادمة", "आपकी आगामी बैठकों के लिए शांत मौसम", "yaklaşan randevularınız için sakin hava", "spokojna pogoda na twoje nadchodzące spotkania", "cuaca tenang untuk janji temu Anda yang akan datang", "спокійна погода для ваших майбутніх зустрічей", "thời tiết yên ả cho các cuộc hẹn sắp tới của bạn"
        ),
        "weather_today_word" to t(
            "Vandaag", "Today", "Hoy", "Hoje", "Heute", "Aujourd'hui", "Oggi", "오늘", "今天", "今日",
            "Сегодня", "اليوم", "आज", "Bugün", "Dziś", "Hari ini", "Сьогодні", "Hôm nay"
        ),
        "weather_today_chip" to t(
            "vandaag · {label}", "today · {label}", "hoy · {label}", "hoje · {label}", "heute · {label}", "aujourd'hui · {label}", "oggi · {label}", "오늘 · {label}", "今天 · {label}", "今日 · {label}",
            "сегодня · {label}", "اليوم · {label}", "आज · {label}", "bugün · {label}", "dziś · {label}", "hari ini · {label}", "сьогодні · {label}", "hôm nay · {label}"
        ),
        "weather_tomorrow_chip" to t(
            "morgen · {label}", "tomorrow · {label}", "mañana · {label}", "amanhã · {label}", "morgen · {label}", "demain · {label}", "domani · {label}", "내일 · {label}", "明天 · {label}", "明日 · {label}",
            "завтра · {label}", "غدًا · {label}", "कल · {label}", "yarın · {label}", "jutro · {label}", "besok · {label}", "завтра · {label}", "ngày mai · {label}"
        ),
        "weather_speed_unit" to t(
            "km/u", "km/h", "km/h", "km/h", "km/h", "km/h", "km/h", "km/h", "公里/小时", "km/h",
            "км/ч", "كم/س", "किमी/घंटा", "km/sa", "km/h", "km/jam", "км/год", "km/h"
        ),
        "weather_temp_warm_extreme" to t(
            "erg warm", "very warm", "muy cálido", "muito quente", "sehr warm", "très chaud", "molto caldo", "매우 따뜻함", "非常暖和", "非常に暖かい",
            "очень тепло", "دافئ جدًا", "बहुत गर्म", "çok sıcak", "bardzo ciepło", "sangat hangat", "дуже тепло", "rất ấm"
        ),
        "weather_temp_warm" to t(
            "warm", "warm", "cálido", "quente", "warm", "chaud", "caldo", "따뜻함", "暖和", "暖かい",
            "тепло", "دافئ", "गर्म", "sıcak", "ciepło", "hangat", "тепло", "ấm"
        ),
        "weather_temp_pleasant" to t(
            "aangenaam", "pleasant", "agradable", "agradável", "angenehm", "agréable", "piacevole", "쾌적함", "宜人", "快適",
            "приятно", "لطيف", "सुहावना", "hoş", "przyjemnie", "nyaman", "приємно", "dễ chịu"
        ),
        "weather_temp_cool" to t(
            "fris", "cool", "fresco", "fresco", "frisch", "frais", "fresco", "선선함", "凉爽", "涼しい",
            "прохладно", "منعش", "ठंडा", "serin", "chłodno", "sejuk", "прохолодно", "mát mẻ"
        ),
        "weather_temp_cold" to t(
            "koud", "cold", "frío", "frio", "kalt", "froid", "freddo", "추움", "寒冷", "寒い",
            "холодно", "بارد", "ठंडा", "soğuk", "zimno", "dingin", "холодно", "lạnh"
        ),
        "weather_temp_cold_extreme" to t(
            "erg koud", "very cold", "muy frío", "muito frio", "sehr kalt", "très froid", "molto freddo", "매우 추움", "非常寒冷", "非常に寒い",
            "очень холодно", "بارد جدًا", "बहुत ठंडा", "çok soğuk", "bardzo zimno", "sangat dingin", "дуже холодно", "rất lạnh"
        ),
        "weather_cond_rainy" to t(
            "regenachtig", "rainy", "lluvioso", "chuvoso", "regnerisch", "pluvieux", "piovoso", "비 오는", "多雨", "雨がち",
            "дождливо", "ممطر", "बारिश वाला", "yağmurlu", "deszczowo", "hujan", "дощово", "có mưa"
        ),
        "weather_cond_dry" to t(
            "droog", "dry", "seco", "seco", "trocken", "sec", "secco", "건조함", "干燥", "乾燥",
            "сухо", "جاف", "सूखा", "kuru", "sucho", "kering", "сухо", "khô ráo"
        ),
        "weather_cond_light_wet" to t(
            "licht nat", "slightly wet", "algo húmedo", "levemente úmido", "leicht nass", "légèrement humide", "leggermente umido", "약간 습함", "略湿", "やや湿った",
            "слегка влажно", "رطب قليلاً", "थोड़ा गीला", "hafif ıslak", "lekko mokro", "sedikit basah", "трохи вологo", "hơi ẩm ướt"
        ),
        "weather_cond_wet" to t(
            "nat", "wet", "húmedo", "úmido", "nass", "humide", "umido", "습함", "潮湿", "湿った",
            "влажно", "رطب", "गीला", "ıslak", "mokro", "basah", "вологo", "ẩm ướt"
        ),
        "weather_cond_snowy" to t(
            "sneeuwachtig", "snowy", "nevado", "nevado", "verschneit", "neigeux", "nevoso", "눈이 옴", "多雪", "雪模様",
            "снежно", "مثلج", "बर्फ़ीला", "karlı", "śnieżnie", "bersalju", "сніжно", "có tuyết"
        ),
        "weather_cond_turbulent" to t(
            "onstuimig", "stormy", "tormentoso", "tempestuoso", "stürmisch", "orageux", "burrascoso", "험악함", "狂风暴雨", "荒れ模様",
            "бурно", "عاصف", "तूफ़ानी", "fırtınalı", "burzowo", "bergejolak", "бурхливо", "dữ dội"
        ),
        "weather_summary_combined" to t(
            "{temp} en {condition}", "{temp} and {condition}", "{temp} y {condition}", "{temp} e {condition}", "{temp} und {condition}", "{temp} et {condition}", "{temp} e {condition}", "{condition}하고 {temp}", "{temp}，{condition}", "{temp}で{condition}",
            "{temp}, {condition}", "{temp} و{condition}", "{temp} और {condition}", "{temp} ve {condition}", "{temp} i {condition}", "{temp} dan {condition}", "{temp} і {condition}", "{temp} và {condition}"
        ),
        "weather_wind_calm" to t(
            "Vrijwel windstil", "Almost no wind", "Prácticamente sin viento", "Praticamente sem vento", "Fast windstill", "Quasiment pas de vent", "Quasi assenza di vento", "거의 무풍", "几乎无风", "ほぼ無風",
            "Почти безветренно", "لا رياح تقريبًا", "लगभग हवा रहित", "Neredeyse rüzgarsız", "Prawie bezwietrznie", "Hampir tidak berangin", "Майже безвітряно", "Hầu như không có gió"
        ),
        "weather_wind_weak" to t(
            "Zwakke wind", "Light wind", "Viento suave", "Vento fraco", "Schwacher Wind", "Vent faible", "Vento debole", "약한 바람", "微风", "弱い風",
            "Слабый ветер", "رياح خفيفة", "हल्की हवा", "Hafif rüzgar", "Słaby wiatr", "Angin lemah", "Слабкий вітер", "Gió nhẹ"
        ),
        "weather_wind_moderate" to t(
            "Matige wind", "Moderate wind", "Viento moderado", "Vento moderado", "Mäßiger Wind", "Vent modéré", "Vento moderato", "적당한 바람", "中等风", "中程度の風",
            "Умеренный ветер", "رياح معتدلة", "मध्यम हवा", "Orta şiddette rüzgar", "Umiarkowany wiatr", "Angin sedang", "Помірний вітер", "Gió vừa"
        ),
        "weather_wind_strong" to t(
            "Harde wind", "Strong wind", "Viento fuerte", "Vento forte", "Starker Wind", "Vent fort", "Vento forte", "강한 바람", "大风", "強い風",
            "Сильный ветер", "رياح قوية", "तेज़ हवा", "Kuvvetli rüzgar", "Silny wiatr", "Angin kencang", "Сильний вітер", "Gió mạnh"
        ),
        "weather_wind_very_strong" to t(
            "Zeer harde wind", "Very strong wind", "Viento muy fuerte", "Vento muito forte", "Sehr starker Wind", "Vent très fort", "Vento molto forte", "매우 강한 바람", "非常大风", "非常に強い風",
            "Очень сильный ветер", "رياح شديدة القوة", "बहुत तेज़ हवा", "Çok kuvvetli rüzgar", "Bardzo silny wiatr", "Angin sangat kencang", "Дуже сильний вітер", "Gió rất mạnh"
        ),
        "weather_dir_n" to t(
            "noord", "north", "norte", "norte", "Nord", "nord", "nord", "북", "北", "北",
            "север", "شمال", "उत्तर", "kuzey", "północ", "utara", "північ", "bắc"
        ),
        "weather_dir_ne" to t(
            "noordoost", "northeast", "noreste", "nordeste", "Nordost", "nord-est", "nord-est", "북동", "东北", "北東",
            "северо-восток", "شمال شرق", "उत्तर-पूर्व", "kuzeydoğu", "północny wschód", "timur laut", "північний схід", "đông bắc"
        ),
        "weather_dir_e" to t(
            "oost", "east", "este", "leste", "Ost", "est", "est", "동", "东", "東",
            "восток", "شرق", "पूर्व", "doğu", "wschód", "timur", "схід", "đông"
        ),
        "weather_dir_se" to t(
            "zuidoost", "southeast", "sureste", "sudeste", "Südost", "sud-est", "sud-est", "남동", "东南", "南東",
            "юго-восток", "جنوب شرق", "दक्षिण-पूर्व", "güneydoğu", "południowy wschód", "tenggara", "південний схід", "đông nam"
        ),
        "weather_dir_s" to t(
            "zuid", "south", "sur", "sul", "Süd", "sud", "sud", "남", "南", "南",
            "юг", "جنوب", "दक्षिण", "güney", "południe", "selatan", "південь", "nam"
        ),
        "weather_dir_sw" to t(
            "zuidwest", "southwest", "suroeste", "sudoeste", "Südwest", "sud-ouest", "sud-ovest", "남서", "西南", "南西",
            "юго-запад", "جنوب غرب", "दक्षिण-पश्चिम", "güneybatı", "południowy zachód", "barat daya", "південний захід", "tây nam"
        ),
        "weather_dir_w" to t(
            "west", "west", "oeste", "oeste", "West", "ouest", "ovest", "서", "西", "西",
            "запад", "غرب", "पश्चिम", "batı", "zachód", "barat", "захід", "tây"
        ),
        "weather_dir_nw" to t(
            "noordwest", "northwest", "noroeste", "noroeste", "Nordwest", "nord-ouest", "nord-ovest", "북서", "西北", "北西",
            "северо-запад", "شمال غرب", "उत्तर-पश्चिम", "kuzeybatı", "północny zachód", "barat laut", "північний захід", "tây bắc"
        ),
        "weather_condition_clear" to t(
            "Helder", "Clear", "Despejado", "Céu limpo", "Klar", "Dégagé", "Sereno", "맑음", "晴朗", "快晴",
            "Ясно", "صافٍ", "साफ़", "Açık", "Bezchmurnie", "Cerah", "Ясно", "Quang đãng"
        ),
        "weather_condition_mostly_clear" to t(
            "Overwegend helder", "Mostly clear", "Mayormente despejado", "Predominantemente limpo", "Überwiegend klar", "Généralement dégagé", "Prevalentemente sereno", "대체로 맑음", "大部晴朗", "概ね晴れ",
            "Преимущественно ясно", "صافٍ في الغالب", "अधिकतर साफ़", "Genellikle açık", "Przeważnie bezchmurnie", "Umumnya cerah", "Переважно ясно", "Phần lớn quang đãng"
        ),
        "weather_condition_partly_cloudy" to t(
            "Half bewolkt", "Partly cloudy", "Parcialmente nublado", "Parcialmente nublado", "Teilweise bewölkt", "Partiellement nuageux", "Parzialmente nuvoloso", "부분 흐림", "多云", "所により曇り",
            "Переменная облачность", "غائم جزئيًا", "आंशिक बादल", "Parçalı bulutlu", "Częściowo pochmurno", "Berawan sebagian", "Мінлива хмарність", "Nhiều mây từng phần"
        ),
        "weather_condition_cloudy" to t(
            "Bewolkt", "Cloudy", "Nublado", "Nublado", "Bewölkt", "Nuageux", "Nuvoloso", "흐림", "多云", "曇り",
            "Облачно", "غائم", "बादल", "Bulutlu", "Pochmurno", "Berawan", "Хмарно", "Nhiều mây"
        ),
        "weather_condition_fog" to t(
            "Mist", "Fog", "Niebla", "Nevoeiro", "Nebel", "Brouillard", "Nebbia", "안개", "雾", "霧",
            "Туман", "ضباب", "कोहरा", "Sis", "Mgła", "Kabut", "Туман", "Sương mù"
        ),
        "weather_condition_drizzle" to t(
            "Motregen", "Drizzle", "Llovizna", "Chuvisco", "Nieselregen", "Bruine", "Pioviggine", "이슬비", "毛毛雨", "霧雨",
            "Морось", "رذاذ", "बूंदाबांदी", "Çisenti", "Mżawka", "Gerimis", "Мряка", "Mưa phùn"
        ),
        "weather_condition_freezing_drizzle" to t(
            "IJzel (motregen)", "Freezing drizzle", "Llovizna helada", "Chuvisco congelante", "Gefrierender Nieselregen", "Bruine verglaçante", "Pioviggine gelata", "어는 이슬비", "冻毛毛雨", "着氷性の霧雨",
            "Ледяная морось", "رذاذ متجمد", "जमने वाली बूंदाबांदी", "Donan çisenti", "Marznąca mżawka", "Gerimis beku", "Крижана мряка", "Mưa phùn đóng băng"
        ),
        "weather_condition_rain" to t(
            "Regen", "Rain", "Lluvia", "Chuva", "Regen", "Pluie", "Pioggia", "비", "雨", "雨",
            "Дождь", "مطر", "बारिश", "Yağmur", "Deszcz", "Hujan", "Дощ", "Mưa"
        ),
        "weather_condition_freezing_rain" to t(
            "IJzel", "Freezing rain", "Lluvia helada", "Chuva congelante", "Gefrierender Regen", "Pluie verglaçante", "Pioggia gelata", "어는 비", "冻雨", "着氷性の雨",
            "Ледяной дождь", "مطر متجمد", "जमने वाली बारिश", "Donan yağmur", "Marznący deszcz", "Hujan beku", "Крижаний дощ", "Mưa đóng băng"
        ),
        "weather_condition_snow" to t(
            "Sneeuw", "Snow", "Nieve", "Neve", "Schnee", "Neige", "Neve", "눈", "雪", "雪",
            "Снег", "ثلج", "बर्फ़", "Kar", "Śnieg", "Salju", "Сніг", "Tuyết"
        ),
        "weather_condition_snow_showers" to t(
            "Sneeuwbuien", "Snow showers", "Nevadas", "Aguaceiros de neve", "Schneeschauer", "Averses de neige", "Rovesci di neve", "눈 소나기", "阵雪", "にわか雪",
            "Снегопады", "زخات ثلج", "बर्फ़ की बौछारें", "Kar sağanağı", "Przelotne opady śniegu", "Hujan salju sebentar", "Снігопади", "Mưa tuyết rào"
        ),
        "weather_condition_rain_showers" to t(
            "Regenbuien", "Rain showers", "Chubascos", "Aguaceiros", "Regenschauer", "Averses", "Rovesci", "소나기", "阵雨", "にわか雨",
            "Ливни", "زخات مطر", "बौछारें", "Sağanak yağmur", "Przelotne opady deszczu", "Hujan sebentar", "Зливи", "Mưa rào"
        ),
        "weather_condition_thunderstorm" to t(
            "Onweer", "Thunderstorm", "Tormenta eléctrica", "Trovoada", "Gewitter", "Orage", "Temporale", "천둥번개", "雷暴", "雷雨",
            "Гроза", "عاصفة رعدية", "गरज-तूफान", "Gök gürültülü fırtına", "Burza", "Badai petir", "Гроза", "Giông bão"
        ),
        "weather_condition_hail" to t(
            "Hagel", "Hail", "Granizo", "Granizo", "Hagel", "Grêle", "Grandine", "우박", "冰雹", "雹",
            "Град", "برد", "ओलावृष्टि", "Dolu", "Grad", "Hujan es", "Град", "Mưa đá"
        ),
        "weather_condition_unknown" to t(
            "Onbekend", "Unknown", "Desconocido", "Desconhecido", "Unbekannt", "Inconnu", "Sconosciuto", "알 수 없음", "未知", "不明",
            "Неизвестно", "غير معروف", "अज्ञात", "Bilinmiyor", "Nieznana", "Tidak diketahui", "Невідомо", "Không xác định"
        ),
        "weather_evening_warmer" to t(
            "morgen ongeveer {degrees}°C warmer", "about {degrees}°C warmer tomorrow", "mañana unos {degrees}°C más cálido", "amanhã cerca de {degrees}°C mais quente", "morgen etwa {degrees}°C wärmer", "environ {degrees}°C plus chaud demain", "domani circa {degrees}°C più caldo", "내일은 약 {degrees}°C 더 따뜻함", "明天大约高{degrees}°C", "明日は約{degrees}°C暖かい",
            "завтра примерно на {degrees}°C теплее", "أدفأ بحوالي {degrees}° مئوية غدًا", "कल लगभग {degrees}°C अधिक गर्म", "yarın yaklaşık {degrees}°C daha sıcak", "jutro około {degrees}°C cieplej", "besok sekitar {degrees}°C lebih hangat", "завтра приблизно на {degrees}°C тепліше", "ngày mai ấm hơn khoảng {degrees}°C"
        ),
        "weather_evening_colder" to t(
            "morgen ongeveer {degrees}°C kouder", "about {degrees}°C colder tomorrow", "mañana unos {degrees}°C más frío", "amanhã cerca de {degrees}°C mais frio", "morgen etwa {degrees}°C kälter", "environ {degrees}°C plus froid demain", "domani circa {degrees}°C più freddo", "내일은 약 {degrees}°C 더 추움", "明天大约低{degrees}°C", "明日は約{degrees}°C寒い",
            "завтра примерно на {degrees}°C холоднее", "أبرد بحوالي {degrees}° مئوية غدًا", "कल लगभग {degrees}°C अधिक ठंडा", "yarın yaklaşık {degrees}°C daha soğuk", "jutro około {degrees}°C zimniej", "besok sekitar {degrees}°C lebih dingin", "завтра приблизно на {degrees}°C холодніше", "ngày mai lạnh hơn khoảng {degrees}°C"
        ),
        "weather_range_exact" to t(
            "exact tijdstip", "exact time", "hora exacta", "horário exato", "genauer Zeitpunkt", "heure exacte", "orario esatto", "정확한 시각", "确切时间", "正確な時刻",
            "точное время", "الوقت الدقيق", "सटीक समय", "tam saat", "dokładny czas", "waktu tepat", "точний час", "thời điểm chính xác"
        ),
        "weather_min_unit" to t(
            "{min} min", "{min} min", "{min} min", "{min} min", "{min} Min.", "{min} min", "{min} min", "{min}분", "{min}分钟", "{min}分",
            "{min} мин", "{min} دقيقة", "{min} मिनट", "{min} dk", "{min} min", "{min} mnt", "{min} хв", "{min} phút"
        ),
        "weather_hour_unit" to t(
            "{hours} uur", "{hours}h", "{hours} h", "{hours} h", "{hours} Std.", "{hours} h", "{hours} h", "{hours}시간", "{hours}小时", "{hours}時間",
            "{hours} ч", "{hours} س", "{hours} घंटे", "{hours} sa", "{hours} godz.", "{hours} jam", "{hours} год", "{hours} giờ"
        ),
        "weather_hour_min_unit" to t(
            "{hours} uur {min} min", "{hours}h {min}min", "{hours} h {min} min", "{hours} h {min} min", "{hours} Std. {min} Min.", "{hours} h {min} min", "{hours} h {min} min", "{hours}시간 {min}분", "{hours}小时{min}分钟", "{hours}時間{min}分",
            "{hours} ч {min} мин", "{hours} س {min} دقيقة", "{hours} घंटे {min} मिनट", "{hours} sa {min} dk", "{hours} godz. {min} min", "{hours} jam {min} mnt", "{hours} год {min} хв", "{hours} giờ {min} phút"
        ),
        "weather_exact_at_start" to t(
            "Exact bij aanvang", "Exactly at start", "Exactamente al inicio", "Exatamente no início", "Genau zu Beginn", "Exactement au début", "Esattamente all'inizio", "정확히 시작 시점", "恰好在开始时", "開始時ちょうど",
            "Точно в начале", "بالضبط عند البدء", "ठीक शुरुआत में", "Tam başlangıçta", "Dokładnie na początku", "Tepat saat mulai", "Точно на початку", "Chính xác lúc bắt đầu"
        ),
        "weather_minutes_before_suffix" to t(
            "{time} van tevoren", "{time} in advance", "{time} de antelación", "{time} de antecedência", "{time} vorher", "{time} à l'avance", "{time} in anticipo", "{time} 전", "提前{time}", "{time}前",
            "за {time} до", "{time} مسبقًا", "{time} पहले", "{time} önceden", "{time} wcześniej", "{time} sebelumnya", "за {time} до", "trước {time}"
        ),
        "weather_now" to t(
            "nu", "now", "ahora", "agora", "jetzt", "maintenant", "ora", "지금", "现在", "今",
            "сейчас", "الآن", "अभी", "şimdi", "teraz", "sekarang", "зараз", "bây giờ"
        ),
        "weather_in_minutes" to t(
            "over {min} min", "in {min} min", "en {min} min", "em {min} min", "in {min} Min.", "dans {min} min", "tra {min} min", "{min}분 후", "{min}分钟后", "{min}分後",
            "через {min} мин", "خلال {min} دقيقة", "{min} मिनट में", "{min} dk sonra", "za {min} min", "dalam {min} mnt", "через {min} хв", "sau {min} phút"
        ),
        "weather_in_hours" to t(
            "over {hours}u", "in {hours}h", "en {hours} h", "em {hours} h", "in {hours} Std.", "dans {hours} h", "tra {hours} h", "{hours}시간 후", "{hours}小时后", "{hours}時間後",
            "через {hours} ч", "خلال {hours} س", "{hours} घंटे में", "{hours} sa sonra", "za {hours} godz.", "dalam {hours} jam", "через {hours} год", "sau {hours} giờ"
        ),
        "weather_in_hours_minutes" to t(
            "over {hours}u {min}m", "in {hours}h {min}m", "en {hours} h {min} min", "em {hours} h {min} min", "in {hours} Std. {min} Min.", "dans {hours} h {min} min", "tra {hours} h {min} min", "{hours}시간 {min}분 후", "{hours}小时{min}分后", "{hours}時間{min}分後",
            "через {hours} ч {min} мин", "خلال {hours} س {min} دقيقة", "{hours} घंटे {min} मिनट में", "{hours} sa {min} dk sonra", "za {hours} godz. {min} min", "dalam {hours} jam {min} mnt", "через {hours} год {min} хв", "sau {hours} giờ {min} phút"
        ),
        "weather_linked_to" to t(
            "gekoppeld aan · {name}, {time}", "linked to · {name}, {time}", "vinculado a · {name}, {time}", "vinculado a · {name}, {time}", "verknüpft mit · {name}, {time}", "lié à · {name}, {time}", "collegato a · {name}, {time}", "연결됨 · {name}, {time}", "关联到 · {name}，{time}", "紐付け先 · {name}、{time}",
            "связано с · {name}, {time}", "مرتبط بـ · {name}، {time}", "इससे जुड़ा · {name}, {time}", "bağlantılı · {name}, {time}", "powiązane z · {name}, {time}", "terkait dengan · {name}, {time}", "пов'язано з · {name}, {time}", "liên kết với · {name}, {time}"
        ),
        "weather_right_now" to t(
            "op dit moment", "right now", "en este momento", "neste momento", "im Moment", "en ce moment", "in questo momento", "지금", "此刻", "現在",
            "прямо сейчас", "في هذه اللحظة", "अभी इस समय", "şu anda", "w tej chwili", "saat ini", "прямо зараз", "ngay lúc này"
        ),
        "weather_around_event" to t(
            "{combined} · rond {event}", "{combined} · around {event}", "{combined} · alrededor de {event}", "{combined} · por volta de {event}", "{combined} · rund um {event}", "{combined} · autour de {event}", "{combined} · intorno a {event}", "{combined} · {event} 즈음", "{combined} · {event}前后", "{combined} · {event}の前後",
            "{combined} · около {event}", "{combined} · حول {event}", "{combined} · {event} के आसपास", "{combined} · {event} civarında", "{combined} · wokół {event}", "{combined} · sekitar {event}", "{combined} · навколо {event}", "{combined} · quanh {event}"
        ),
        "weather_tomorrow_colon" to t(
            "Morgen: {chance}", "Tomorrow: {chance}", "Mañana: {chance}", "Amanhã: {chance}", "Morgen: {chance}", "Demain : {chance}", "Domani: {chance}", "내일: {chance}", "明天：{chance}", "明日：{chance}",
            "Завтра: {chance}", "غدًا: {chance}", "कल: {chance}", "Yarın: {chance}", "Jutro: {chance}", "Besok: {chance}", "Завтра: {chance}", "Ngày mai: {chance}"
        ),
        "weather_page_title" to t(
            "Weerinstellingen", "Weather settings", "Ajustes de clima", "Configurações de clima", "Wettereinstellungen", "Paramètres météo", "Impostazioni meteo", "날씨 설정", "天气设置", "天気設定",
            "Настройки погоды", "إعدادات الطقس", "मौसम सेटिंग्स", "Hava durumu ayarları", "Ustawienia pogody", "Pengaturan cuaca", "Налаштування погоди", "Cài đặt thời tiết"
        ),
        "weather_nav_general" to t(
            "Algemeen", "General", "General", "Geral", "Allgemein", "Général", "Generale", "일반", "常规", "一般",
            "Общие", "عام", "सामान्य", "Genel", "Ogólne", "Umum", "Загальні", "Chung"
        ),
        "weather_nav_location" to t(
            "Locatie", "Location", "Ubicación", "Localização", "Standort", "Emplacement", "Posizione", "위치", "位置", "位置",
            "Местоположение", "الموقع", "स्थान", "Konum", "Lokalizacja", "Lokasi", "Місцезнаходження", "Vị trí"
        ),
        "weather_nav_model" to t(
            "Weermodel", "Weather model", "Modelo meteorológico", "Modelo meteorológico", "Wettermodell", "Modèle météo", "Modello meteo", "날씨 모델", "天气模型", "気象モデル",
            "Погодная модель", "نموذج الطقس", "मौसम मॉडल", "Hava durumu modeli", "Model pogodowy", "Model cuaca", "Погодна модель", "Mô hình thời tiết"
        ),
        "weather_label_bad_weather" to t(
            "Slecht weer", "Bad weather", "Mal tiempo", "Mau tempo", "Schlechtes Wetter", "Mauvais temps", "Maltempo", "악천후", "恶劣天气", "悪天候",
            "Плохая погода", "طقس سيئ", "खराब मौसम", "Kötü hava", "Zła pogoda", "Cuaca buruk", "Погана погода", "Thời tiết xấu"
        ),
        "weather_nav_temp_change" to t(
            "Grote temperatuurverandering", "Large temperature change", "Gran cambio de temperatura", "Grande mudança de temperatura", "Große Temperaturänderung", "Grand changement de température", "Grande variazione di temperatura", "큰 기온 변화", "大幅温度变化", "大きな気温変化",
            "Значительное изменение температуры", "تغير كبير في درجة الحرارة", "बड़ा तापमान परिवर्तन", "Büyük sıcaklık değişimi", "Duża zmiana temperatury", "Perubahan suhu besar", "Значна зміна температури", "Thay đổi nhiệt độ lớn"
        ),
        "weather_nav_notifications" to t(
            "Meldingen", "Notifications", "Notificaciones", "Notificações", "Benachrichtigungen", "Notifications", "Notifiche", "알림", "通知", "通知",
            "Уведомления", "الإشعارات", "सूचनाएं", "Bildirimler", "Powiadomienia", "Notifikasi", "Сповіщення", "Thông báo"
        ),
        "weather_nav_widgets" to t(
            "Widgets", "Widgets", "Widgets", "Widgets", "Widgets", "Widgets", "Widget", "위젯", "小组件", "ウィジェット",
            "Виджеты", "الأدوات", "विजेट्स", "Widget'lar", "Widżety", "Widget", "Віджети", "Tiện ích"
        ),
        "weather_nav_display" to t(
            "Weergave", "Display", "Visualización", "Exibição", "Anzeige", "Affichage", "Visualizzazione", "표시", "显示", "表示",
            "Отображение", "العرض", "प्रदर्शन", "Görünüm", "Wyświetlanie", "Tampilan", "Відображення", "Hiển thị"
        ),
        "weather_label_temp_change_short" to t(
            "Temperatuurverandering", "Temperature change", "Cambio de temperatura", "Mudança de temperatura", "Temperaturänderung", "Changement de température", "Variazione di temperatura", "기온 변화", "温度变化", "気温変化",
            "Изменение температуры", "تغير درجة الحرارة", "तापमान परिवर्तन", "Sıcaklık değişimi", "Zmiana temperatury", "Perubahan suhu", "Зміна температури", "Thay đổi nhiệt độ"
        ),
        "weather_calendar_items_title" to t(
            "Agenda items · {category}", "Calendar items · {category}", "Eventos de calendario · {category}", "Itens da agenda · {category}", "Kalendereinträge · {category}", "Éléments du calendrier · {category}", "Voci del calendario · {category}", "일정 항목 · {category}", "日程项目 · {category}", "予定項目 · {category}",
            "Записи календаря · {category}", "عناصر التقويم · {category}", "कैलेंडर आइटम · {category}", "Takvim öğeleri · {category}", "Elementy kalendarza · {category}", "Item kalender · {category}", "Елементи календаря · {category}", "Mục lịch · {category}"
        ),
        "weather_current_location" to t(
            "Huidige locatie: {name}", "Current location: {name}", "Ubicación actual: {name}", "Localização atual: {name}", "Aktueller Standort: {name}", "Emplacement actuel : {name}", "Posizione attuale: {name}", "현재 위치: {name}", "当前位置：{name}", "現在地：{name}",
            "Текущее местоположение: {name}", "الموقع الحالي: {name}", "वर्तमान स्थान: {name}", "Mevcut konum: {name}", "Bieżąca lokalizacja: {name}", "Lokasi saat ini: {name}", "Поточне місцезнаходження: {name}", "Vị trí hiện tại: {name}"
        ),
        "weather_location_source_gps" to t(
            "Locatiebron: GPS · beweegt automatisch mee", "Location source: GPS · moves automatically", "Fuente de ubicación: GPS · se mueve automáticamente", "Fonte de localização: GPS · move-se automaticamente", "Standortquelle: GPS · bewegt sich automatisch mit", "Source de localisation : GPS · se déplace automatiquement", "Fonte posizione: GPS · si aggiorna automaticamente", "위치 소스: GPS · 자동으로 이동", "位置来源：GPS · 自动跟随移动", "位置情報の元：GPS・自動追従",
            "Источник местоположения: GPS · перемещается автоматически", "مصدر الموقع: GPS · يتحرك تلقائيًا", "स्थान स्रोत: GPS · स्वचालित रूप से चलता है", "Konum kaynağı: GPS · otomatik olarak hareket eder", "Źródło lokalizacji: GPS · porusza się automatycznie", "Sumber lokasi: GPS · bergerak otomatis", "Джерело місцезнаходження: GPS · рухається автоматично", "Nguồn vị trí: GPS · tự động di chuyển"
        ),
        "weather_location_source_fixed" to t(
            "Locatiebron: vast", "Location source: fixed", "Fuente de ubicación: fija", "Fonte de localização: fixa", "Standortquelle: fest", "Source de localisation : fixe", "Fonte posizione: fissa", "위치 소스: 고정", "位置来源：固定", "位置情報の元：固定",
            "Источник местоположения: фиксированный", "مصدر الموقع: ثابت", "स्थान स्रोत: स्थिर", "Konum kaynağı: sabit", "Źródło lokalizacji: stałe", "Sumber lokasi: tetap", "Джерело місцезнаходження: фіксоване", "Nguồn vị trí: cố định"
        ),
        "weather_search_location" to t(
            "Zoek een locatie", "Search a location", "Buscar una ubicación", "Pesquisar um local", "Standort suchen", "Rechercher un lieu", "Cerca una posizione", "위치 검색", "搜索位置", "場所を検索",
            "Поиск местоположения", "ابحث عن موقع", "स्थान खोजें", "Konum ara", "Wyszukaj lokalizację", "Cari lokasi", "Пошук місцезнаходження", "Tìm kiếm vị trí"
        ),
        "weather_use_gps_location" to t(
            "GPS-locatie gebruiken", "Use GPS location", "Usar ubicación GPS", "Usar localização GPS", "GPS-Standort verwenden", "Utiliser la localisation GPS", "Usa posizione GPS", "GPS 위치 사용", "使用 GPS 位置", "GPS位置情報を使用",
            "Использовать GPS-местоположение", "استخدام موقع GPS", "GPS स्थान का उपयोग करें", "GPS konumunu kullan", "Użyj lokalizacji GPS", "Gunakan lokasi GPS", "Використовувати GPS-місцезнаходження", "Sử dụng vị trí GPS"
        ),
        "weather_fetching_location" to t(
            "Locatie ophalen...", "Fetching location...", "Obteniendo ubicación...", "A obter localização...", "Standort wird abgerufen...", "Récupération de la localisation...", "Recupero posizione...", "위치 가져오는 중...", "正在获取位置…", "位置情報を取得中…",
            "Получение местоположения...", "جارٍ جلب الموقع...", "स्थान प्राप्त किया जा रहा है...", "Konum alınıyor...", "Pobieranie lokalizacji...", "Mengambil lokasi...", "Отримання місцезнаходження...", "Đang lấy vị trí..."
        ),
        "weather_no_location_available" to t(
            "Geen locatie beschikbaar, laatste bekende locatie wordt gebruikt", "No location available, using last known location", "No hay ubicación disponible, se usará la última ubicación conocida", "Localização indisponível, será usada a última localização conhecida", "Kein Standort verfügbar, letzter bekannter Standort wird verwendet", "Aucune localisation disponible, dernière localisation connue utilisée", "Nessuna posizione disponibile, verrà usata l'ultima posizione nota", "위치를 사용할 수 없어 마지막으로 알려진 위치를 사용합니다", "无法获取位置，将使用最后已知位置", "位置情報が取得できないため、最後に確認された位置を使用します",
            "Местоположение недоступно, используется последнее известное местоположение", "لا يوجد موقع متاح، سيتم استخدام آخر موقع معروف", "कोई स्थान उपलब्ध नहीं, अंतिम ज्ञात स्थान का उपयोग किया जाएगा", "Konum yok, bilinen son konum kullanılacak", "Brak dostępnej lokalizacji, użyta zostanie ostatnia znana lokalizacja", "Lokasi tidak tersedia, menggunakan lokasi terakhir yang diketahui", "Місцезнаходження недоступне, використовується останнє відоме місцезнаходження", "Không có vị trí khả dụng, sẽ dùng vị trí đã biết gần nhất"
        ),
        "weather_gps_location_fallback" to t(
            "GPS locatie", "GPS location", "Ubicación GPS", "Localização GPS", "GPS-Standort", "Localisation GPS", "Posizione GPS", "GPS 위치", "GPS 位置", "GPS位置",
            "GPS-местоположение", "موقع GPS", "GPS स्थान", "GPS konumu", "Lokalizacja GPS", "Lokasi GPS", "GPS-місцезнаходження", "Vị trí GPS"
        ),
        "weather_auto_update_header" to t(
            "AUTOMATISCH BIJWERKEN", "AUTOMATIC UPDATES", "ACTUALIZACIÓN AUTOMÁTICA", "ATUALIZAÇÃO AUTOMÁTICA", "AUTOMATISCHE AKTUALISIERUNG", "MISE À JOUR AUTOMATIQUE", "AGGIORNAMENTO AUTOMATICO", "자동 업데이트", "自动更新", "自動更新",
            "АВТОМАТИЧЕСКОЕ ОБНОВЛЕНИЕ", "التحديث التلقائي", "स्वचालित अपडेट", "OTOMATİK GÜNCELLEME", "AUTOMATYCZNA AKTUALIZACJA", "PEMBARUAN OTOMATIS", "АВТОМАТИЧНЕ ОНОВЛЕННЯ", "CẬP NHẬT TỰ ĐỘNG"
        ),
        "weather_sync_interval_desc" to t(
            "Controleer elke {min} min of je verplaatst bent (alleen als dat écht zo is, wordt de locatie en het weer bijgewerkt)", "Check every {min} min whether you've moved (only if you really have, location and weather are updated)", "Comprueba cada {min} min si te has movido (solo si es así, se actualizan la ubicación y el clima)", "Verifica a cada {min} min se te moveste (só nesse caso, a localização e o clima são atualizados)", "Alle {min} Min. prüfen, ob du dich bewegt hast (nur dann werden Standort und Wetter aktualisiert)", "Vérifie toutes les {min} min si vous avez bougé (seulement si c'est le cas, la localisation et la météo sont mises à jour)", "Controlla ogni {min} min se ti sei spostato (solo in tal caso, posizione e meteo vengono aggiornati)", "{min}분마다 이동했는지 확인합니다 (실제로 이동한 경우에만 위치와 날씨가 업데이트됩니다)", "每 {min} 分钟检查一次你是否已移动（仅在确实移动时才会更新位置和天气）", "{min}分ごとに移動したか確認します（実際に移動した場合のみ、位置情報と天気が更新されます）",
            "Проверка каждые {min} мин, не переместились ли вы (местоположение и погода обновляются только если это так)", "التحقق كل {min} دقيقة مما إذا كنت قد تحركت (فقط في هذه الحالة يتم تحديث الموقع والطقس)", "हर {min} मिनट में जांचें कि क्या आप स्थानांतरित हुए हैं (केवल तभी स्थान और मौसम अपडेट होगा)", "{min} dakikada bir yer değiştirip değiştirmediğiniz kontrol edilir (yalnızca gerçekten değiştiyseniz konum ve hava durumu güncellenir)", "Sprawdzanie co {min} min, czy się przemieściłeś (tylko wtedy lokalizacja i pogoda są aktualizowane)", "Periksa setiap {min} menit apakah Anda telah berpindah (hanya jika benar berpindah, lokasi dan cuaca diperbarui)", "Перевірка кожні {min} хв, чи ви перемістилися (лише тоді оновлюються місцезнаходження та погода)", "Kiểm tra mỗi {min} phút xem bạn đã di chuyển chưa (chỉ khi thực sự di chuyển, vị trí và thời tiết mới được cập nhật)"
        ),
        "weather_gps_off_title" to t(
            "GPS staat uit", "GPS is off", "El GPS está desactivado", "O GPS está desligado", "GPS ist ausgeschaltet", "Le GPS est désactivé", "Il GPS è disattivato", "GPS가 꺼져 있습니다", "GPS 已关闭", "GPSがオフになっています",
            "GPS выключен", "خدمة GPS متوقفة", "GPS बंद है", "GPS kapalı", "GPS jest wyłączony", "GPS mati", "GPS вимкнено", "GPS đang tắt"
        ),
        "weather_gps_off_message" to t(
            "Zet locatieservices aan om je locatie automatisch te bepalen", "Turn on location services to determine your location automatically", "Activa los servicios de ubicación para determinar tu ubicación automáticamente", "Ative os serviços de localização para determinar a sua localização automaticamente", "Aktiviere Standortdienste, um deinen Standort automatisch zu bestimmen", "Activez les services de localisation pour déterminer automatiquement votre position", "Attiva i servizi di localizzazione per determinare automaticamente la tua posizione", "위치를 자동으로 확인하려면 위치 서비스를 켜세요", "开启定位服务以自动确定您的位置", "位置情報サービスをオンにすると自動で位置を特定します",
            "Включите службы геолокации, чтобы определять местоположение автоматически", "فعّل خدمات الموقع لتحديد موقعك تلقائيًا", "अपना स्थान स्वचालित रूप से निर्धारित करने के लिए स्थान सेवाएं चालू करें", "Konumunuzu otomatik olarak belirlemek için konum hizmetlerini açın", "Włącz usługi lokalizacji, aby automatycznie określać swoją lokalizację", "Aktifkan layanan lokasi untuk menentukan lokasi Anda secara otomatis", "Увімкніть служби визначення місцезнаходження, щоб визначати його автоматично", "Bật dịch vụ vị trí để tự động xác định vị trí của bạn"
        ),
        "weather_gps_off_settings_button" to t(
            "Instellingen", "Settings", "Ajustes", "Definições", "Einstellungen", "Paramètres", "Impostazioni", "설정", "设置", "設定",
            "Настройки", "الإعدادات", "सेटिंग्स", "Ayarlar", "Ustawienia", "Pengaturan", "Налаштування", "Cài đặt"
        ),
        "weather_cancel" to t(
            "Annuleren", "Cancel", "Cancelar", "Cancelar", "Abbrechen", "Annuler", "Annulla", "취소", "取消", "キャンセル",
            "Отмена", "إلغاء", "रद्द करें", "İptal", "Anuluj", "Batal", "Скасувати", "Hủy"
        ),
        "weather_model_description" to t(
            "Verschillende weerdiensten gebruiken andere modellen — dat verklaart afwijkingen met bv. Google Weer. Kies hier zelf een bron, of laat Open-Meteo automatisch het beste model kiezen.", "Different weather services use different models — this explains differences with, e.g., Google Weather. Choose a source yourself here, or let Open-Meteo automatically pick the best model.", "Diferentes servicios meteorológicos usan modelos distintos, lo que explica diferencias con, por ejemplo, Google Clima. Elige aquí tu propia fuente o deja que Open-Meteo elija automáticamente el mejor modelo.", "Diferentes serviços meteorológicos usam modelos diferentes — isso explica diferenças com, por exemplo, o Google Clima. Escolhe aqui a tua própria fonte, ou deixa o Open-Meteo escolher automaticamente o melhor modelo.", "Verschiedene Wetterdienste verwenden unterschiedliche Modelle — das erklärt Abweichungen z. B. zu Google Wetter. Wähle hier selbst eine Quelle, oder lass Open-Meteo automatisch das beste Modell wählen.", "Différents services météo utilisent des modèles différents — cela explique les écarts avec, par exemple, Google Météo. Choisissez ici votre propre source, ou laissez Open-Meteo choisir automatiquement le meilleur modèle.", "Servizi meteo diversi usano modelli diversi — questo spiega le differenze con, ad esempio, Google Meteo. Scegli qui la tua fonte, oppure lascia che Open-Meteo scelga automaticamente il modello migliore.", "날씨 서비스마다 다른 모델을 사용합니다. 이는 예를 들어 구글 날씨와의 차이를 설명합니다. 여기서 직접 소스를 선택하거나 Open-Meteo가 자동으로 최적의 모델을 선택하도록 하세요.", "不同的天气服务使用不同的模型——这解释了与例如谷歌天气的差异。您可以在此自行选择数据源，或让 Open-Meteo 自动选择最佳模型。", "気象サービスによって異なるモデルが使われており、これがGoogle天気などとの違いの理由です。ここで自分でソースを選ぶか、Open-Meteoに最適なモデルを自動選択させることができます。",
            "Разные погодные сервисы используют разные модели — это объясняет расхождения, например, с Google Погода. Выберите источник здесь сами, или позвольте Open-Meteo автоматически выбрать лучшую модель.", "تستخدم خدمات الطقس المختلفة نماذج مختلفة — وهذا يفسر الاختلافات مع، على سبيل المثال، طقس Google. اختر مصدرًا هنا بنفسك، أو دع Open-Meteo يختار أفضل نموذج تلقائيًا.", "विभिन्न मौसम सेवाएं अलग-अलग मॉडल का उपयोग करती हैं — यह उदाहरण के लिए गूगल मौसम के साथ अंतर को समझाता है। यहां स्वयं एक स्रोत चुनें, या Open-Meteo को स्वचालित रूप से सर्वश्रेष्ठ मॉडल चुनने दें।", "Farklı hava durumu servisleri farklı modeller kullanır — bu, örneğin Google Hava Durumu ile olan farkları açıklar. Burada kendiniz bir kaynak seçin veya Open-Meteo'nun otomatik olarak en iyi modeli seçmesine izin verin.", "Różne serwisy pogodowe korzystają z różnych modeli — to tłumaczy rozbieżności np. z Pogodą Google. Wybierz tutaj źródło samodzielnie lub pozwól Open-Meteo automatycznie wybrać najlepszy model.", "Layanan cuaca yang berbeda menggunakan model yang berbeda — ini menjelaskan perbedaan dengan, misalnya, Google Cuaca. Pilih sumber sendiri di sini, atau biarkan Open-Meteo otomatis memilih model terbaik.", "Різні погодні сервіси використовують різні моделі — це пояснює розбіжності, наприклад, з Google Погода. Виберіть джерело тут самостійно, або дозвольте Open-Meteo автоматично вибрати найкращу модель.", "Các dịch vụ thời tiết khác nhau sử dụng các mô hình khác nhau — điều này giải thích sự khác biệt với, ví dụ, Google Thời tiết. Hãy tự chọn nguồn tại đây, hoặc để Open-Meteo tự động chọn mô hình tốt nhất."
        ),
        "weather_model_auto_label" to t(
            "Automatisch (aanbevolen)", "Automatic (recommended)", "Automático (recomendado)", "Automático (recomendado)", "Automatisch (empfohlen)", "Automatique (recommandé)", "Automatico (consigliato)", "자동 (권장)", "自动（推荐）", "自動（推奨）",
            "Автоматически (рекомендуется)", "تلقائي (موصى به)", "स्वचालित (अनुशंसित)", "Otomatik (önerilen)", "Automatyczny (zalecane)", "Otomatis (disarankan)", "Автоматично (рекомендовано)", "Tự động (khuyến nghị)"
        ),
        "weather_model_auto_desc" to t(
            "Open-Meteo kiest zelf het best passende model voor je locatie", "Open-Meteo automatically picks the best-fitting model for your location", "Open-Meteo elige automáticamente el modelo más adecuado para tu ubicación", "O Open-Meteo escolhe automaticamente o modelo mais adequado para a tua localização", "Open-Meteo wählt selbst das am besten passende Modell für deinen Standort", "Open-Meteo choisit lui-même le modèle le mieux adapté à votre emplacement", "Open-Meteo sceglie da solo il modello più adatto alla tua posizione", "Open-Meteo가 위치에 가장 적합한 모델을 자동으로 선택합니다", "Open-Meteo 会自动为您的位置选择最合适的模型", "Open-Meteoがあなたの位置に最も適したモデルを自動で選択します",
            "Open-Meteo сам выбирает наиболее подходящую модель для вашего местоположения", "يختار Open-Meteo تلقائيًا النموذج الأنسب لموقعك", "Open-Meteo आपके स्थान के लिए स्वयं सबसे उपयुक्त मॉडल चुनता है", "Open-Meteo, konumunuz için en uygun modeli kendisi seçer", "Open-Meteo automatycznie wybiera najlepiej dopasowany model dla Twojej lokalizacji", "Open-Meteo secara otomatis memilih model yang paling sesuai untuk lokasi Anda", "Open-Meteo сам обирає найбільш підходящу модель для вашого місцезнаходження", "Open-Meteo tự động chọn mô hình phù hợp nhất cho vị trí của bạn"
        ),
        "weather_model_ecmwf_desc" to t(
            "Europees model, vaak gebruikt als referentie door professionele weerdiensten", "European model, often used as a reference by professional weather services", "Modelo europeo, a menudo usado como referencia por servicios meteorológicos profesionales", "Modelo europeu, frequentemente usado como referência por serviços meteorológicos profissionais", "Europäisches Modell, oft als Referenz von professionellen Wetterdiensten genutzt", "Modèle européen, souvent utilisé comme référence par les services météo professionnels", "Modello europeo, spesso usato come riferimento dai servizi meteo professionali", "유럽 모델로, 전문 기상 서비스에서 참조로 자주 사용됩니다", "欧洲模型，常被专业气象服务用作参考", "ヨーロッパのモデルで、プロの気象サービスの基準としてよく使われます",
            "Европейская модель, часто используется как эталон профессиональными метеослужбами", "نموذج أوروبي، يُستخدم غالبًا كمرجع من قبل خدمات الطقس الاحترافية", "यूरोपीय मॉडल, अक्सर पेशेवर मौसम सेवाओं द्वारा संदर्भ के रूप में उपयोग किया जाता है", "Avrupa modeli, profesyonel hava durumu servisleri tarafından sıkça referans olarak kullanılır", "Model europejski, często używany jako punkt odniesienia przez profesjonalne serwisy pogodowe", "Model Eropa, sering digunakan sebagai referensi oleh layanan cuaca profesional", "Європейська модель, часто використовується як еталон професійними метеослужбами", "Mô hình châu Âu, thường được các dịch vụ thời tiết chuyên nghiệp dùng làm tham chiếu"
        ),
        "weather_model_knmi_desc" to t(
            "Nederlands model, hoge resolutie voor West-Europa", "Dutch model, high resolution for Western Europe", "Modelo neerlandés, alta resolución para Europa Occidental", "Modelo neerlandês, alta resolução para a Europa Ocidental", "Niederländisches Modell, hohe Auflösung für Westeuropa", "Modèle néerlandais, haute résolution pour l'Europe de l'Ouest", "Modello olandese, alta risoluzione per l'Europa occidentale", "네덜란드 모델로, 서유럽에 대해 고해상도를 제공합니다", "荷兰模型，针对西欧提供高分辨率", "オランダのモデルで、西ヨーロッパ向けの高解像度データです",
            "Голландская модель, высокое разрешение для Западной Европы", "نموذج هولندي، بدقة عالية لأوروبا الغربية", "डच मॉडल, पश्चिमी यूरोप के लिए उच्च रिज़ॉल्यूशन", "Hollanda modeli, Batı Avrupa için yüksek çözünürlük", "Model holenderski, wysoka rozdzielczość dla Europy Zachodniej", "Model Belanda, resolusi tinggi untuk Eropa Barat", "Голландська модель, висока роздільна здатність для Західної Європи", "Mô hình Hà Lan, độ phân giải cao cho Tây Âu"
        ),
        "weather_model_icon_desc" to t(
            "Duitse nationale weerdienst", "German national weather service", "Servicio meteorológico nacional alemán", "Serviço meteorológico nacional alemão", "Deutscher nationaler Wetterdienst", "Service météorologique national allemand", "Servizio meteorologico nazionale tedesco", "독일 국립 기상청", "德国国家气象局", "ドイツ国立気象局",
            "Национальная метеослужба Германии", "الهيئة الوطنية الألمانية للأرصاد الجوية", "जर्मन राष्ट्रीय मौसम सेवा", "Alman ulusal hava durumu servisi", "Niemiecka krajowa służba meteorologiczna", "Layanan cuaca nasional Jerman", "Національна метеослужба Німеччини", "Cơ quan khí tượng quốc gia Đức"
        ),
        "weather_model_gfs_desc" to t(
            "Amerikaans model, wereldwijde dekking", "American model, global coverage", "Modelo estadounidense, cobertura mundial", "Modelo americano, cobertura mundial", "Amerikanisches Modell, weltweite Abdeckung", "Modèle américain, couverture mondiale", "Modello americano, copertura mondiale", "미국 모델로, 전 세계를 포괄합니다", "美国模型，覆盖全球", "アメリカのモデルで、世界規模をカバーします",
            "Американская модель, глобальное покрытие", "نموذج أمريكي، بتغطية عالمية", "अमेरिकी मॉडल, वैश्विक कवरेज", "Amerikan modeli, küresel kapsama", "Model amerykański, zasięg globalny", "Model Amerika, cakupan global", "Американська модель, глобальне покриття", "Mô hình Mỹ, phủ sóng toàn cầu"
        ),
        "weather_model_ukmo_desc" to t(
            "Britse nationale weerdienst", "British national weather service", "Servicio meteorológico nacional británico", "Serviço meteorológico nacional britânico", "Britischer nationaler Wetterdienst", "Service météorologique national britannique", "Servizio meteorologico nazionale britannico", "영국 국립 기상청", "英国国家气象局", "英国国立気象局",
            "Национальная метеослужба Великобритании", "الهيئة الوطنية البريطانية للأرصاد الجوية", "ब्रिटिश राष्ट्रीय मौसम सेवा", "İngiliz ulusal hava durumu servisi", "Brytyjska krajowa służba meteorologiczna", "Layanan cuaca nasional Inggris", "Національна метеослужба Великої Британії", "Cơ quan khí tượng quốc gia Anh"
        ),
        "weather_model_meteofrance_desc" to t(
            "Franse nationale weerdienst", "French national weather service", "Servicio meteorológico nacional francés", "Serviço meteorológico nacional francês", "Französischer nationaler Wetterdienst", "Service météorologique national français", "Servizio meteorologico nazionale francese", "프랑스 국립 기상청", "法国国家气象局", "フランス国立気象局",
            "Национальная метеослужба Франции", "الهيئة الوطنية الفرنسية للأرصاد الجوية", "फ्रांसीसी राष्ट्रीय मौसम सेवा", "Fransız ulusal hava durumu servisi", "Francuska krajowa służba meteorologiczna", "Layanan cuaca nasional Prancis", "Національна метеослужба Франції", "Cơ quan khí tượng quốc gia Pháp"
        ),
        "weather_model_gem_desc" to t(
            "Canadese nationale weerdienst", "Canadian national weather service", "Servicio meteorológico nacional canadiense", "Serviço meteorológico nacional canadiano", "Kanadischer nationaler Wetterdienst", "Service météorologique national canadien", "Servizio meteorologico nazionale canadese", "캐나다 국립 기상청", "加拿大国家气象局", "カナダ国立気象局",
            "Национальная метеослужба Канады", "الهيئة الوطنية الكندية للأرصاد الجوية", "कनाडाई राष्ट्रीय मौसम सेवा", "Kanada ulusal hava durumu servisi", "Kanadyjska krajowa służba meteorologiczna", "Layanan cuaca nasional Kanada", "Національна метеослужба Канади", "Cơ quan khí tượng quốc gia Canada"
        ),
        "weather_section_rain_alarm" to t(
            "REGENALARM", "RAIN ALARM", "ALARMA DE LLUVIA", "ALARME DE CHUVA", "REGENALARM", "ALARME PLUIE", "ALLARME PIOGGIA", "강우 알림", "降雨提醒", "雨アラート",
            "ДОЖДЕВОЕ ОПОВЕЩЕНИЕ", "تنبيه المطر", "बारिश अलार्म", "YAĞMUR ALARMI", "ALARM DESZCZOWY", "ALARM HUJAN", "ДОЩОВЕ СПОВІЩЕННЯ", "CẢNH BÁO MƯA"
        ),
        "weather_rain_alarm_toggle" to t(
            "Regenalarm", "Rain alarm", "Alarma de lluvia", "Alarme de chuva", "Regenalarm", "Alarme pluie", "Allarme pioggia", "강우 알림", "降雨提醒", "雨アラート",
            "Дождевое оповещение", "تنبيه المطر", "बारिश अलार्म", "Yağmur alarmı", "Alarm deszczowy", "Alarm hujan", "Дощове сповіщення", "Cảnh báo mưa"
        ),
        "weather_rain_threshold_desc" to t(
            "Drempel: {percent}% neerslagkans", "Threshold: {percent}% chance of precipitation", "Umbral: {percent}% de probabilidad de precipitación", "Limite: {percent}% de probabilidade de precipitação", "Schwelle: {percent}% Niederschlagswahrscheinlichkeit", "Seuil : {percent}% de risque de précipitations", "Soglia: {percent}% di probabilità di precipitazioni", "임계값: 강수 확률 {percent}%", "阈值：降水概率 {percent}%", "しきい値：降水確率{percent}%",
            "Порог: {percent}% вероятность осадков", "الحد: {percent}% احتمال هطول الأمطار", "सीमा: वर्षा की संभावना {percent}%", "Eşik: %{percent} yağış olasılığı", "Próg: {percent}% szans na opady", "Ambang: {percent}% kemungkinan hujan", "Поріг: {percent}% ймовірність опадів", "Ngưỡng: {percent}% khả năng có mưa"
        ),
        "weather_nav_extra_bad_weather" to t(
            "Slechtweer", "Bad weather", "Mal tiempo", "Mau tempo", "Schlechtes Wetter", "Mauvais temps", "Maltempo", "악천후", "恶劣天气", "悪天候",
            "Плохая погода", "طقس سيئ", "खराब मौसम", "Kötü hava", "Zła pogoda", "Cuaca buruk", "Погана погода", "Thời tiết xấu"
        ),
        "weather_extra_conditions_desc" to t(
            "Extra weersomstandigheden zoals storm, hagel, gladde weg en orkaan", "Extra weather conditions such as storm, hail, icy roads and hurricane", "Condiciones meteorológicas adicionales como tormenta, granizo, carreteras heladas y huracán", "Condições meteorológicas extra como tempestade, granizo, estrada gelada e furacão", "Zusätzliche Wetterbedingungen wie Sturm, Hagel, Glatteis und Hurrikan", "Conditions météo supplémentaires comme tempête, grêle, routes verglacées et ouragan", "Condizioni meteo aggiuntive come tempesta, grandine, strade ghiacciate e uragano", "폭풍, 우박, 빙판길, 허리케인 등 추가 기상 조건", "额外的天气状况，如暴风、冰雹、路面结冰和飓风", "嵐、雹、路面凍結、ハリケーンなどの追加の気象条件",
            "Дополнительные погодные условия, такие как шторм, град, гололёд и ураган", "ظروف جوية إضافية مثل العاصفة والبرد والطرق الجليدية والإعصار", "तूफान, ओले, बर्फीली सड़कें और तूफान जैसी अतिरिक्त मौसम स्थितियां", "Fırtına, dolu, buzlu yol ve kasırga gibi ek hava koşulları", "Dodatkowe warunki pogodowe, takie jak burza, grad, oblodzone drogi i huragan", "Kondisi cuaca tambahan seperti badai, hujan es, jalan licin, dan angin topan", "Додаткові погодні умови, такі як шторм, град, ожеледиця та ураган", "Các điều kiện thời tiết bổ sung như bão, mưa đá, đường trơn trượt và cuồng phong"
        ),
        "weather_section_when_warn" to t(
            "WANNEER WAARSCHUWEN", "WHEN TO WARN", "CUÁNDO AVISAR", "QUANDO AVISAR", "WANN WARNEN", "QUAND AVERTIR", "QUANDO AVVISARE", "경고 시점", "何时提醒", "警告するタイミング",
            "КОГДА ПРЕДУПРЕЖДАТЬ", "متى يتم التحذير", "कब चेतावनी दें", "NE ZAMAN UYARILSIN", "KIEDY OSTRZEGAĆ", "KAPAN MEMPERINGATKAN", "КОЛИ ПОПЕРЕДЖАТИ", "KHI NÀO CẢNH BÁO"
        ),
        "weather_day_before" to t(
            "Dag ervoor", "Day before", "Día anterior", "Dia anterior", "Tag zuvor", "Veille", "Giorno prima", "전날", "前一天", "前日",
            "За день до", "اليوم السابق", "एक दिन पहले", "Bir gün önce", "Dzień wcześniej", "Sehari sebelumnya", "За день до", "Ngày trước đó"
        ),
        "weather_same_day_morning" to t(
            "Zelfde dag 's ochtends", "Same day, in the morning", "Mismo día, por la mañana", "Mesmo dia, de manhã", "Am selben Tag morgens", "Le jour même, le matin", "Stesso giorno, al mattino", "당일 아침", "当天早上", "当日の朝",
            "В тот же день утром", "في نفس اليوم صباحًا", "उसी दिन सुबह", "Aynı gün sabahı", "Tego samego dnia rano", "Hari yang sama, pagi hari", "У той самий день вранці", "Cùng ngày vào buổi sáng"
        ),
        "weather_notify_before_event" to t(
            "Melding voor agenda item", "Notification before calendar event", "Notificación antes del evento de calendario", "Notificação antes do evento da agenda", "Benachrichtigung vor Kalendereintrag", "Notification avant l'événement du calendrier", "Notifica prima dell'evento in calendario", "일정 항목 전 알림", "日程事件前提醒", "予定の前に通知",
            "Уведомление перед событием календаря", "إشعار قبل حدث التقويم", "कैलेंडर इवेंट से पहले सूचना", "Takvim etkinliğinden önce bildirim", "Powiadomienie przed wydarzeniem w kalendarzu", "Notifikasi sebelum acara kalender", "Сповіщення перед подією календаря", "Thông báo trước sự kiện lịch"
        ),
        "weather_no_max_test" to t(
            "Geen maximum (test)", "No maximum (test)", "Sin máximo (prueba)", "Sem máximo (teste)", "Kein Maximum (Test)", "Aucun maximum (test)", "Nessun massimo (test)", "최대치 없음(테스트)", "无上限（测试）", "上限なし（テスト）",
            "Без максимума (тест)", "بدون حد أقصى (اختبار)", "कोई अधिकतम नहीं (परीक्षण)", "Maksimum yok (test)", "Bez maksimum (test)", "Tanpa maksimum (uji coba)", "Без максимуму (тест)", "Không giới hạn tối đa (thử nghiệm)"
        ),
        "weather_no_max_test_desc" to t(
            "Tijdelijk, om tijdens testen niet tegen de dag-limiet aan te lopen", "Temporary, to avoid hitting the daily limit while testing", "Temporal, para no toparse con el límite diario durante las pruebas", "Temporário, para não atingir o limite diário durante os testes", "Vorübergehend, damit man beim Testen nicht an das Tageslimit stößt", "Temporaire, pour ne pas atteindre la limite journalière pendant les tests", "Temporaneo, per non raggiungere il limite giornaliero durante i test", "테스트 중 일일 한도에 걸리지 않도록 하는 임시 설정입니다", "临时设置，避免测试时触发每日上限", "テスト中に1日の上限に達しないようにするための一時的な設定です",
            "Временно, чтобы не упереться в дневной лимит во время тестирования", "مؤقت، لتجنب الوصول إلى الحد اليومي أثناء الاختبار", "परीक्षण के दौरान दैनिक सीमा से न टकराने के लिए अस्थायी", "Test sırasında günlük sınıra takılmamak için geçici", "Tymczasowe, aby podczas testów nie natrafić na dzienny limit", "Sementara, agar tidak mencapai batas harian saat menguji", "Тимчасово, щоб не натрапити на денний ліміт під час тестування", "Tạm thời, để không chạm giới hạn hàng ngày khi thử nghiệm"
        ),
        "weather_max_per_day" to t(
            "Max. {count} melding(en) per dag", "Max. {count} notification(s) per day", "Máx. {count} notificación(es) por día", "Máx. {count} notificação(ões) por dia", "Max. {count} Benachrichtigung(en) pro Tag", "Max. {count} notification(s) par jour", "Max. {count} notifica/e al giorno", "하루 최대 {count}개 알림", "每天最多 {count} 条提醒", "1日最大{count}件の通知",
            "Макс. {count} уведомление(й) в день", "الحد الأقصى {count} إشعار(ات) يوميًا", "अधिकतम {count} सूचना/एं प्रति दिन", "Günde en fazla {count} bildirim", "Maks. {count} powiadomień dziennie", "Maks. {count} notifikasi per hari", "Макс. {count} сповіщень на день", "Tối đa {count} thông báo mỗi ngày"
        ),
        // Bereik: geldt voor alle slecht-weer-meldingen samen (Dag ervoor, Zelfde dag én de melding
        // rond een agenda-item), vandaar een eigen kopje boven de drie triggers.
        "weather_section_scope" to t(
            "Waar wordt naar gekeken", "What is checked", "Qué se comprueba", "O que é verificado", "Was geprüft wird", "Ce qui est vérifié", "Cosa viene controllato", "확인 범위", "查看范围", "確認する範囲",
            "Что проверяется", "ما يتم فحصه", "क्या जाँचा जाता है", "Neye bakılıyor", "Co jest sprawdzane", "Yang diperiksa", "Що перевіряється", "Phạm vi kiểm tra"
        ),
        "weather_scope_desc_whole_day" to t(
            "Alle meldingen kijken naar het weer van de hele dag.",
            "All alerts look at the weather for the whole day.",
            "Todos los avisos consideran el tiempo de todo el día.",
            "Todos os alertas consideram o tempo do dia inteiro.",
            "Alle Meldungen betrachten das Wetter des ganzen Tages.",
            "Toutes les alertes examinent la météo de toute la journée.",
            "Tutti gli avvisi considerano il meteo dell'intera giornata.",
            "모든 알림이 하루 전체의 날씨를 확인합니다.",
            "所有提醒都查看全天的天气。",
            "すべての通知が一日全体の天気を確認します。",
            "Все уведомления учитывают погоду за весь день.",
            "تنظر جميع التنبيهات إلى طقس اليوم بأكمله.",
            "सभी सूचनाएँ पूरे दिन के मौसम को देखती हैं।",
            "Tüm bildirimler günün tamamındaki havaya bakar.",
            "Wszystkie powiadomienia sprawdzają pogodę całego dnia.",
            "Semua notifikasi melihat cuaca sepanjang hari.",
            "Усі сповіщення враховують погоду за весь день.",
            "Tất cả thông báo xem thời tiết cả ngày."
        ),
        "weather_scope_desc_events" to t(
            "Alle meldingen kijken alleen naar de uren rond je agenda-afspraken.",
            "All alerts only look at the hours around your calendar events.",
            "Todos los avisos solo consideran las horas cercanas a tus eventos.",
            "Todos os alertas só consideram as horas próximas aos seus compromissos.",
            "Alle Meldungen betrachten nur die Stunden rund um deine Termine.",
            "Toutes les alertes n'examinent que les heures autour de vos rendez-vous.",
            "Tutti gli avvisi considerano solo le ore attorno ai tuoi appuntamenti.",
            "모든 알림이 일정 전후의 시간만 확인합니다.",
            "所有提醒只查看日程前后的时间。",
            "すべての通知が予定の前後の時間だけを確認します。",
            "Все уведомления учитывают только часы вокруг ваших встреч.",
            "تنظر جميع التنبيهات فقط إلى الساعات المحيطة بمواعيدك.",
            "सभी सूचनाएँ केवल आपके कैलेंडर कार्यक्रमों के आसपास के घंटों को देखती हैं।",
            "Tüm bildirimler yalnızca randevularının çevresindeki saatlere bakar.",
            "Wszystkie powiadomienia sprawdzają tylko godziny wokół twoich wydarzeń.",
            "Semua notifikasi hanya melihat jam di sekitar agenda Anda.",
            "Усі сповіщення враховують лише години навколо ваших подій.",
            "Tất cả thông báo chỉ xem các giờ quanh lịch hẹn của bạn."
        ),
        "weather_scope_events_no_items" to t(
            "Staat er die dag niets in je agenda, dan komt er geen melding.",
            "No events that day means no alert.",
            "Si no hay eventos ese día, no hay aviso.",
            "Se não houver compromissos nesse dia, não há alerta.",
            "Stehen an dem Tag keine Termine an, kommt keine Meldung.",
            "S'il n'y a aucun rendez-vous ce jour-là, aucune alerte n'est envoyée.",
            "Se quel giorno non ci sono appuntamenti, non arriva alcun avviso.",
            "그날 일정이 없으면 알림도 없습니다.",
            "当天没有日程就不会有提醒。",
            "その日に予定がなければ通知はありません。",
            "Если в этот день нет встреч, уведомления не будет.",
            "إذا لم تكن هناك مواعيد في ذلك اليوم، فلن يصلك تنبيه.",
            "उस दिन कोई कार्यक्रम न हो तो कोई सूचना नहीं आएगी।",
            "O gün ajandanda hiçbir şey yoksa bildirim gelmez.",
            "Jeśli tego dnia nie masz wydarzeń, nie otrzymasz powiadomienia.",
            "Jika tidak ada agenda hari itu, tidak ada notifikasi.",
            "Якщо того дня немає подій, сповіщення не буде.",
            "Nếu hôm đó không có lịch hẹn thì sẽ không có thông báo."
        ),

        "weather_whole_day_toggle" to t(
            "Hele dag i.p.v. exact tijdstip", "Whole day instead of exact time", "Todo el día en lugar de la hora exacta", "Dia inteiro em vez da hora exata", "Ganzer Tag statt genauer Uhrzeit", "Toute la journée au lieu de l'heure exacte", "Tutto il giorno invece dell'orario esatto", "정확한 시간 대신 하루 종일", "全天而非精确时间", "正確な時刻ではなく終日",
            "Весь день вместо точного времени", "طوال اليوم بدلاً من الوقت الدقيق", "सटीक समय के बजाय पूरा दिन", "Kesin saat yerine tüm gün", "Cały dzień zamiast dokładnej godziny", "Sepanjang hari, bukan waktu tepat", "Весь день замість точного часу", "Cả ngày thay vì thời điểm chính xác"
        ),
        "weather_whole_day_desc_on" to t(
            "Kijkt naar de algemene voorspelling van de hele dag, melding blijft rond het agenda-item", "Looks at the overall forecast for the whole day, notification stays around the calendar event", "Analiza la previsión general de todo el día, la notificación se mantiene alrededor del evento de calendario", "Analisa a previsão geral do dia inteiro, a notificação mantém-se em torno do evento da agenda", "Berücksichtigt die allgemeine Vorhersage für den ganzen Tag, Benachrichtigung bleibt rund um den Kalendereintrag", "Regarde les prévisions générales de toute la journée, la notification reste autour de l'événement du calendrier", "Considera le previsioni generali dell'intera giornata, la notifica resta intorno all'evento in calendario", "하루 전체의 전반적인 예보를 확인하며, 알림은 일정 항목 주변에 유지됩니다", "查看全天的总体预报，提醒仍围绕日程事件发出", "1日全体の全般的な予報を確認し、通知は予定の前後に表示されます",
            "Учитывает общий прогноз на весь день, уведомление остаётся привязанным к событию календаря", "ينظر إلى التوقعات العامة لليوم بأكمله، ويبقى الإشعار حول حدث التقويم", "पूरे दिन के सामान्य पूर्वानुमान को देखता है, सूचना कैलेंडर इवेंट के आसपास ही रहती है", "Tüm günün genel tahminine bakar, bildirim takvim etkinliği civarında kalır", "Uwzględnia ogólną prognozę na cały dzień, powiadomienie pozostaje wokół wydarzenia w kalendarzu", "Melihat prakiraan umum sepanjang hari, notifikasi tetap di sekitar acara kalender", "Враховує загальний прогноз на весь день, сповіщення залишається прив'язаним до події календаря", "Xem xét dự báo chung cho cả ngày, thông báo vẫn xoay quanh sự kiện lịch"
        ),
        "weather_whole_day_desc_off" to t(
            "Kijkt puur naar het weer op het exacte tijdstip van het agenda-item", "Looks purely at the weather at the exact time of the calendar event", "Analiza exclusivamente el clima en la hora exacta del evento de calendario", "Analisa exclusivamente o clima na hora exata do evento da agenda", "Berücksichtigt ausschließlich das Wetter zum genauen Zeitpunkt des Kalendereintrags", "Regarde uniquement la météo à l'heure exacte de l'événement du calendrier", "Considera esclusivamente il meteo nell'orario esatto dell'evento in calendario", "일정 항목의 정확한 시간에 해당하는 날씨만 확인합니다", "只查看日程事件精确时间点的天气", "予定の正確な時刻の天気のみを確認します",
            "Учитывает погоду только в точное время события календаря", "ينظر فقط إلى الطقس في الوقت الدقيق لحدث التقويم", "केवल कैलेंडर इवेंट के सटीक समय पर मौसम को देखता है", "Yalnızca takvim etkinliğinin tam saatindeki hava durumuna bakar", "Uwzględnia wyłącznie pogodę w dokładnym momencie wydarzenia w kalendarzu", "Hanya melihat cuaca pada waktu tepat acara kalender", "Враховує погоду лише в точний час події календаря", "Chỉ xem xét thời tiết vào đúng thời điểm sự kiện lịch"
        ),
        "weather_range_exact_no_margin" to t(
            "Bereik: exact tijdstip (geen marge)", "Range: exact time (no margin)", "Rango: hora exacta (sin margen)", "Intervalo: hora exata (sem margem)", "Bereich: genaue Uhrzeit (keine Marge)", "Plage : heure exacte (aucune marge)", "Intervallo: orario esatto (nessun margine)", "범위: 정확한 시간(여유 없음)", "范围：精确时间（无余量）", "範囲：正確な時刻（余裕なし）",
            "Диапазон: точное время (без запаса)", "النطاق: الوقت الدقيق (بدون هامش)", "सीमा: सटीक समय (कोई मार्जिन नहीं)", "Aralık: tam saat (marj yok)", "Zakres: dokładna godzina (bez marginesu)", "Rentang: waktu tepat (tanpa margin)", "Діапазон: точний час (без запасу)", "Phạm vi: đúng thời điểm (không có biên độ)"
        ),
        "weather_range_around" to t(
            "Bereik: ± {range} rond het tijdstip", "Range: ± {range} around the time", "Rango: ± {range} alrededor de la hora", "Intervalo: ± {range} em torno da hora", "Bereich: ± {range} um die Uhrzeit", "Plage : ± {range} autour de l'heure", "Intervallo: ± {range} intorno all'orario", "범위: 시간 기준 ± {range}", "范围：时间点前后 ± {range}", "範囲：時刻の前後 ± {range}",
            "Диапазон: ± {range} вокруг времени", "النطاق: ± {range} حول الوقت", "सीमा: समय के आसपास ± {range}", "Aralık: saat civarında ± {range}", "Zakres: ± {range} wokół godziny", "Rentang: ± {range} di sekitar waktu", "Діапазон: ± {range} навколо часу", "Phạm vi: ± {range} quanh thời điểm"
        ),
        "weather_range_before_after_desc" to t(
            "Er wordt {each} vóór de afspraak en {each} ná de afspraak gekeken om te bepalen hoe de weersomstandigheid is", "The weather is checked {each} before the event and {each} after the event to determine conditions", "Se analiza el clima {each} antes de la cita y {each} después de la cita para determinar las condiciones", "O clima é verificado {each} antes do compromisso e {each} depois do compromisso para determinar as condições", "Das Wetter wird {each} vor und {each} nach dem Termin geprüft, um die Bedingungen zu bestimmen", "La météo est vérifiée {each} avant le rendez-vous et {each} après le rendez-vous pour déterminer les conditions", "Il meteo viene controllato {each} prima dell'appuntamento e {each} dopo per determinare le condizioni", "약속 {each} 전과 {each} 후의 날씨를 확인하여 상태를 판단합니다", "系统会查看约会前 {each} 和约会后 {each} 的天气以判断天气状况", "予定の{each}前と{each}後の天気を確認して状態を判断します",
            "Погода проверяется за {each} до встречи и через {each} после неё для определения условий", "يتم فحص الطقس {each} قبل الموعد و{each} بعده لتحديد الظروف", "अपॉइंटमेंट से {each} पहले और {each} बाद के मौसम की जांच की जाती है", "Randevudan {each} önce ve {each} sonra hava durumu kontrol edilir", "Pogoda jest sprawdzana {each} przed spotkaniem i {each} po spotkaniu, aby określić warunki", "Cuaca diperiksa {each} sebelum janji dan {each} setelah janji untuk menentukan kondisi", "Погода перевіряється за {each} до зустрічі та через {each} після неї, щоб визначити умови", "Thời tiết được kiểm tra {each} trước cuộc hẹn và {each} sau cuộc hẹn để xác định điều kiện"
        ),
        "weather_test_now_button" to t(
            "Test nu (agenda-meldingen)", "Test now (calendar notifications)", "Probar ahora (notificaciones de calendario)", "Testar agora (notificações da agenda)", "Jetzt testen (Kalenderbenachrichtigungen)", "Tester maintenant (notifications du calendrier)", "Prova ora (notifiche calendario)", "지금 테스트(일정 알림)", "立即测试（日程提醒）", "今すぐテスト（予定通知）",
            "Проверить сейчас (уведомления календаря)", "اختبار الآن (إشعارات التقويم)", "अभी परीक्षण करें (कैलेंडर सूचनाएं)", "Şimdi test et (takvim bildirimleri)", "Testuj teraz (powiadomienia z kalendarza)", "Uji sekarang (notifikasi kalender)", "Перевірити зараз (сповіщення календаря)", "Kiểm tra ngay (thông báo lịch)"
        ),
        "weather_test_now_toast" to t(
            "Wordt nu gecontroleerd (agenda-meldingen)…", "Checking now (calendar notifications)…", "Comprobando ahora (notificaciones de calendario)…", "A verificar agora (notificações da agenda)…", "Wird jetzt geprüft (Kalenderbenachrichtigungen)…", "Vérification en cours (notifications du calendrier)…", "Verifica in corso (notifiche calendario)…", "지금 확인 중(일정 알림)…", "正在检查（日程提醒）…", "確認中（予定通知）…",
            "Проверка выполняется (уведомления календаря)…", "جارٍ التحقق الآن (إشعارات التقويم)…", "अभी जांचा जा रहा है (कैलेंडर सूचनाएं)…", "Şimdi kontrol ediliyor (takvim bildirimleri)…", "Sprawdzanie teraz (powiadomienia z kalendarza)…", "Sedang memeriksa (notifikasi kalender)…", "Перевіряється зараз (сповіщення календаря)…", "Đang kiểm tra (thông báo lịch)…"
        ),
        "weather_test_now_desc" to t(
            "Forceert direct 1 check en wist steeds de test-status — mag dus meerdere keren achter elkaar, geldt voor slecht weer én temp-verandering samen", "Forces one immediate check and always resets the test status — so it can be run multiple times in a row, applies to bad weather and temperature change together", "Fuerza una comprobación inmediata y siempre restablece el estado de prueba, por lo que se puede ejecutar varias veces seguidas; se aplica tanto al mal tiempo como al cambio de temperatura", "Força uma verificação imediata e reinicia sempre o estado de teste — pode, portanto, ser executado várias vezes seguidas, aplica-se tanto ao mau tempo como à mudança de temperatura", "Erzwingt sofort eine Prüfung und setzt den Teststatus stets zurück — kann also mehrmals hintereinander ausgeführt werden, gilt für schlechtes Wetter und Temperaturänderung zusammen", "Force une vérification immédiate et réinitialise toujours l'état de test — peut donc être exécuté plusieurs fois de suite, s'applique au mauvais temps et au changement de température ensemble", "Forza subito un controllo e azzera sempre lo stato del test — può quindi essere eseguito più volte di seguito, vale sia per il maltempo che per il cambio di temperatura", "즉시 1회 확인을 강제 실행하고 테스트 상태를 항상 초기화합니다 — 따라서 여러 번 연속 실행할 수 있으며, 악천후와 기온 변화 모두에 적용됩니다", "立即强制执行一次检查并始终重置测试状态——因此可以连续多次运行，同时适用于恶劣天气和温度变化", "即座に1回のチェックを強制実行し、テスト状態を常にリセットします。そのため連続して何度でも実行でき、悪天候と気温変化の両方に適用されます",
            "Немедленно запускает одну проверку и всегда сбрасывает тестовый статус — поэтому можно запускать несколько раз подряд, действует для плохой погоды и изменения температуры вместе", "يفرض فحصًا فوريًا واحدًا ويعيد دائمًا تعيين حالة الاختبار — لذا يمكن تشغيله عدة مرات متتالية، وينطبق على الطقس السيئ وتغير درجة الحرارة معًا", "तुरंत एक जांच बलपूर्वक करता है और हमेशा परीक्षण स्थिति रीसेट करता है — इसलिए इसे कई बार लगातार चलाया जा सकता है, यह खराब मौसम और तापमान परिवर्तन दोनों पर लागू होता है", "Hemen bir kontrol zorlar ve test durumunu her zaman sıfırlar — bu yüzden art arda birden çok kez çalıştırılabilir, kötü hava ve sıcaklık değişimi için birlikte geçerlidir", "Wymusza natychmiastowe 1 sprawdzenie i zawsze resetuje stan testu — można więc uruchamiać wielokrotnie pod rząd, dotyczy zarówno złej pogody, jak i zmiany temperatury razem", "Memaksa 1 pemeriksaan langsung dan selalu mereset status uji — sehingga dapat dijalankan berkali-kali berturut-turut, berlaku untuk cuaca buruk dan perubahan suhu sekaligus", "Негайно виконує 1 перевірку і завжди скидає тестовий статус — тому можна запускати кілька разів поспіль, стосується поганої погоди та зміни температури разом", "Buộc thực hiện ngay 1 lần kiểm tra và luôn đặt lại trạng thái thử nghiệm — do đó có thể chạy nhiều lần liên tiếp, áp dụng cho cả thời tiết xấu và thay đổi nhiệt độ"
        ),
        "weather_section_calendars" to t(
            "AGENDA'S", "CALENDARS", "CALENDARIOS", "AGENDAS", "KALENDER", "CALENDRIERS", "CALENDARI", "일정", "日历", "カレンダー",
            "КАЛЕНДАРИ", "التقويمات", "कैलेंडर", "TAKVİMLER", "KALENDARZE", "KALENDER", "КАЛЕНДАРІ", "LỊCH"
        ),
        "weather_choose_calendars_bad_weather" to t(
            "Kies welke agenda's meetellen voor slecht-weer-meldingen", "Choose which calendars count for bad-weather notifications", "Elige qué calendarios cuentan para las notificaciones de mal tiempo", "Escolhe quais agendas contam para as notificações de mau tempo", "Wähle, welche Kalender für Schlechtwetter-Benachrichtigungen zählen", "Choisissez les calendriers qui comptent pour les notifications de mauvais temps", "Scegli quali calendari contano per le notifiche di maltempo", "악천후 알림에 반영할 일정을 선택하세요", "选择哪些日历计入恶劣天气提醒", "悪天候通知に反映するカレンダーを選択してください",
            "Выберите, какие календари учитываются для уведомлений о плохой погоде", "اختر التقويمات التي تُحتسب لإشعارات الطقس السيئ", "चुनें कि कौन से कैलेंडर खराब मौसम सूचनाओं के लिए गिने जाएं", "Kötü hava bildirimleri için hangi takvimlerin sayılacağını seçin", "Wybierz, które kalendarze liczą się do powiadomień o złej pogodzie", "Pilih kalender mana yang dihitung untuk notifikasi cuaca buruk", "Виберіть, які календарі враховуються для сповіщень про погану погоду", "Chọn lịch nào được tính cho thông báo thời tiết xấu"
        ),
        "weather_nav_calendar_items" to t(
            "Agenda items", "Calendar items", "Eventos de calendario", "Itens da agenda", "Kalendereinträge", "Éléments du calendrier", "Voci del calendario", "일정 항목", "日程项目", "予定項目",
            "Записи календаря", "عناصر التقويم", "कैलेंडर आइटम", "Takvim öğeleri", "Elementy kalendarza", "Item kalender", "Елементи календаря", "Mục lịch"
        ),
        "weather_extra_conditions_title" to t(
            "Extra weersomstandigheden", "Extra weather conditions", "Condiciones meteorológicas adicionales", "Condições meteorológicas extra", "Zusätzliche Wetterbedingungen", "Conditions météo supplémentaires", "Condizioni meteo aggiuntive", "추가 기상 조건", "额外天气状况", "追加の気象条件",
            "Дополнительные погодные условия", "ظروف جوية إضافية", "अतिरिक्त मौसम स्थितियां", "Ek hava koşulları", "Dodatkowe warunki pogodowe", "Kondisi cuaca tambahan", "Додаткові погодні умови", "Điều kiện thời tiết bổ sung"
        ),
        "weather_extra_conditions_page_desc" to t(
            "Deze typen gebruiken dezelfde agenda-selectie en dezelfde 'wanneer waarschuwen'-instellingen als de rest van Slecht weer. Standaard staat alles aan.", "These types use the same calendar selection and the same 'when to warn' settings as the rest of Bad weather. Everything is on by default.", "Estos tipos usan la misma selección de calendarios y los mismos ajustes de 'cuándo avisar' que el resto de Mal tiempo. Por defecto todo está activado.", "Estes tipos usam a mesma seleção de agendas e as mesmas definições de 'quando avisar' que o resto de Mau tempo. Por predefinição, tudo está ativado.", "Diese Typen verwenden dieselbe Kalenderauswahl und dieselben 'Wann warnen'-Einstellungen wie der Rest von Schlechtes Wetter. Standardmäßig ist alles aktiviert.", "Ces types utilisent la même sélection de calendriers et les mêmes paramètres 'quand avertir' que le reste de Mauvais temps. Tout est activé par défaut.", "Questi tipi usano la stessa selezione di calendari e le stesse impostazioni 'quando avvisare' del resto di Maltempo. Per impostazione predefinita, tutto è attivo.", "이 유형들은 나머지 악천후 설정과 동일한 일정 선택 및 '경고 시점' 설정을 사용합니다. 기본적으로 모두 켜져 있습니다.", "这些类型使用与其余恶劣天气设置相同的日历选择和'何时提醒'设置。默认全部开启。", "これらのタイプは、他の悪天候の設定と同じカレンダー選択と「警告するタイミング」設定を使用します。デフォルトではすべてオンになっています。",
            "Эти типы используют тот же выбор календарей и те же настройки 'когда предупреждать', что и остальная часть раздела Плохая погода. По умолчанию всё включено.", "تستخدم هذه الأنواع نفس اختيار التقويم ونفس إعدادات 'متى يتم التحذير' كما هو الحال في بقية الطقس السيئ. كل شيء مفعّل افتراضيًا.", "ये प्रकार शेष खराब मौसम के समान कैलेंडर चयन और समान 'कब चेतावनी दें' सेटिंग्स का उपयोग करते हैं। डिफ़ॉल्ट रूप से सब कुछ चालू है।", "Bu türler, Kötü havanın geri kalanıyla aynı takvim seçimini ve aynı 'ne zaman uyarılsın' ayarlarını kullanır. Varsayılan olarak hepsi açıktır.", "Te typy używają tego samego wyboru kalendarzy i tych samych ustawień 'kiedy ostrzegać' co reszta Złej pogody. Domyślnie wszystko jest włączone.", "Jenis ini menggunakan pemilihan kalender yang sama dan pengaturan 'kapan memperingatkan' yang sama seperti Cuaca buruk lainnya. Secara default semuanya aktif.", "Ці типи використовують той самий вибір календарів і ті самі налаштування 'коли попереджати', що й решта розділу Погана погода. За замовчуванням усе увімкнено.", "Các loại này sử dụng cùng lựa chọn lịch và cùng cài đặt 'khi nào cảnh báo' như phần còn lại của Thời tiết xấu. Mặc định tất cả đều bật."
        ),
        "weather_section_wind" to t(
            "WIND", "WIND", "VIENTO", "VENTO", "WIND", "VENT", "VENTO", "바람", "风", "風",
            "ВЕТЕР", "الرياح", "हवा", "RÜZGAR", "WIATR", "ANGIN", "ВІТЕР", "GIÓ"
        ),
        "weather_storm_toggle" to t(
            "Storm", "Storm", "Tormenta", "Tempestade", "Sturm", "Tempête", "Tempesta", "폭풍", "暴风", "嵐",
            "Шторм", "عاصفة", "तूफान", "Fırtına", "Burza", "Badai", "Шторм", "Bão"
        ),
        "weather_gusts_from" to t(
            "Windstoten vanaf {speed} km/u", "Gusts from {speed} km/h", "Ráfagas desde {speed} km/h", "Rajadas a partir de {speed} km/h", "Windböen ab {speed} km/h", "Rafales à partir de {speed} km/h", "Raffiche da {speed} km/h", "{speed}km/h 이상의 돌풍", "阵风达 {speed} 公里/小时以上", "{speed}km/h以上の突風",
            "Порывы от {speed} км/ч", "هبات رياح من {speed} كم/س", "{speed} किमी/घंटा से हवा के झोंके", "{speed} km/sa üzeri rüzgar hamleleri", "Porywy od {speed} km/h", "Hembusan angin mulai {speed} km/jam", "Пориви від {speed} км/год", "Gió giật từ {speed} km/h"
        ),
        "weather_hurricane_toggle" to t(
            "Orkaan verwacht", "Hurricane expected", "Huracán previsto", "Furacão previsto", "Hurrikan erwartet", "Ouragan attendu", "Uragano previsto", "허리케인 예상", "预计有飓风", "ハリケーンの予報",
            "Ожидается ураган", "إعصار متوقع", "तूफान अपेक्षित", "Kasırga bekleniyor", "Oczekiwany huragan", "Angin topan diperkirakan", "Очікується ураган", "Dự kiến có cuồng phong"
        ),
        "weather_section_precipitation" to t(
            "NEERSLAG", "PRECIPITATION", "PRECIPITACIÓN", "PRECIPITAÇÃO", "NIEDERSCHLAG", "PRÉCIPITATIONS", "PRECIPITAZIONI", "강수", "降水", "降水",
            "ОСАДКИ", "هطول الأمطار", "वर्षा", "YAĞIŞ", "OPADY", "CURAH HUJAN", "ОПАДИ", "LƯỢNG MƯA"
        ),
        "weather_snow_toggle" to t(
            "Sneeuw", "Snow", "Nieve", "Neve", "Schnee", "Neige", "Neve", "눈", "雪", "雪",
            "Снег", "ثلج", "बर्फ", "Kar", "Śnieg", "Salju", "Сніг", "Tuyết"
        ),
        "weather_wet_snow_toggle" to t(
            "Natte sneeuw", "Wet snow", "Nieve húmeda", "Neve molhada", "Nassschnee", "Neige mouillée", "Neve bagnata", "습설", "湿雪", "湿った雪",
            "Мокрый снег", "ثلج رطب", "गीली बर्फ", "Islak kar", "Mokry śnieg", "Salju basah", "Мокрий сніг", "Tuyết ướt"
        ),
        "weather_hail_toggle" to t(
            "Hagel", "Hail", "Granizo", "Granizo", "Hagel", "Grêle", "Grandine", "우박", "冰雹", "雹",
            "Град", "برد", "ओले", "Dolu", "Grad", "Hujan es", "Град", "Mưa đá"
        ),
        "weather_ice_road_toggle" to t(
            "Gladde weg / ijzel", "Icy roads / black ice", "Carreteras heladas / hielo", "Estrada gelada / gelo", "Glätte / Blitzeis", "Route glissante / verglas", "Strade ghiacciate / gelicidio", "빙판길/도로 결빙", "路面结冰/黑冰", "凍結路面／路面凍結",
            "Гололёд / ледяная корка", "طرق زلقة / جليد أسود", "बर्फीली सड़कें / काली बर्फ", "Buzlu yol / kara buz", "Oblodzona droga / gołoledź", "Jalan licin / es hitam", "Ожеледиця / чорний лід", "Đường trơn trượt / băng đen"
        ),
        "weather_section_temperature" to t(
            "TEMPERATUUR", "TEMPERATURE", "TEMPERATURA", "TEMPERATURA", "TEMPERATUR", "TEMPÉRATURE", "TEMPERATURA", "온도", "温度", "気温",
            "ТЕМПЕРАТУРА", "درجة الحرارة", "तापमान", "SICAKLIK", "TEMPERATURA", "SUHU", "ТЕМПЕРАТУРА", "NHIỆT ĐỘ"
        ),
        "weather_extreme_heat_toggle" to t(
            "Extreme hitte", "Extreme heat", "Calor extremo", "Calor extremo", "Extreme Hitze", "Chaleur extrême", "Calore estremo", "폭염", "极端高温", "猛暑",
            "Экстремальная жара", "حر شديد", "अत्यधिक गर्मी", "Aşırı sıcaklık", "Ekstremalny upał", "Panas ekstrem", "Екстремальна спека", "Nắng nóng cực đoan"
        ),
        "weather_from_temp" to t(
            "Vanaf {temp}°C", "From {temp}°C", "Desde {temp}°C", "A partir de {temp}°C", "Ab {temp}°C", "À partir de {temp}°C", "Da {temp}°C", "{temp}°C 이상", "{temp}°C 起", "{temp}°C以上",
            "От {temp}°C", "من {temp}°م", "{temp}°C से", "{temp}°C üzeri", "Od {temp}°C", "Mulai {temp}°C", "Від {temp}°C", "Từ {temp}°C"
        ),
        "weather_temp_change_intro" to t(
            "Los van slecht weer: dit gaat over een groot verschil in temperatuur tussen dagen, niet over regen, sneeuw of onweer.", "Separate from bad weather: this is about a large temperature difference between days, not about rain, snow or thunderstorms.", "Independiente del mal tiempo: esto trata sobre una gran diferencia de temperatura entre días, no sobre lluvia, nieve o tormentas.", "Separado do mau tempo: isto refere-se a uma grande diferença de temperatura entre dias, não a chuva, neve ou trovoadas.", "Unabhängig von schlechtem Wetter: Hier geht es um einen großen Temperaturunterschied zwischen Tagen, nicht um Regen, Schnee oder Gewitter.", "Indépendant du mauvais temps : il s'agit d'une grande différence de température entre les jours, pas de pluie, de neige ou d'orage.", "Indipendente dal maltempo: riguarda una grande differenza di temperatura tra i giorni, non pioggia, neve o temporali.", "악천후와는 별개로, 이는 비, 눈, 천둥이 아니라 날짜 간 큰 기온 차이에 관한 것입니다.", "与恶劣天气无关：这涉及不同日期间的巨大温差，而非降雨、降雪或雷暴。", "悪天候とは別に、これは雨や雪、雷雨ではなく、日ごとの大きな気温差についてです。",
            "Отдельно от плохой погоды: речь идёт о большой разнице температур между днями, а не о дожде, снеге или грозе.", "بمعزل عن الطقس السيئ: يتعلق هذا بفرق كبير في درجة الحرارة بين الأيام، وليس بالمطر أو الثلج أو العواصف الرعدية.", "खराब मौसम से अलग: यह दिनों के बीच बड़े तापमान अंतर के बारे में है, बारिश, बर्फ या आंधी के बारे में नहीं।", "Kötü havadan bağımsız: bu, günler arasındaki büyük sıcaklık farkıyla ilgilidir, yağmur, kar veya fırtınayla değil.", "Niezależnie od złej pogody: chodzi o dużą różnicę temperatury między dniami, a nie o deszcz, śnieg czy burzę.", "Terpisah dari cuaca buruk: ini tentang perbedaan suhu besar antar hari, bukan tentang hujan, salju, atau badai petir.", "Окремо від поганої погоди: йдеться про велику різницю температур між днями, а не про дощ, сніг чи грозу.", "Tách biệt với thời tiết xấu: điều này liên quan đến sự chênh lệch nhiệt độ lớn giữa các ngày, không phải mưa, tuyết hay giông bão."
        ),
        "weather_section_temp_change" to t(
            "TEMPERATUURWISSEL", "TEMPERATURE CHANGE", "CAMBIO DE TEMPERATURA", "MUDANÇA DE TEMPERATURA", "TEMPERATURWECHSEL", "CHANGEMENT DE TEMPÉRATURE", "VARIAZIONE DI TEMPERATURA", "기온 변화", "温度变化", "気温変化",
            "ИЗМЕНЕНИЕ ТЕМПЕРАТУРЫ", "تغير درجة الحرارة", "तापमान परिवर्तन", "SICAKLIK DEĞİŞİMİ", "ZMIANA TEMPERATURY", "PERUBAHAN SUHU", "ЗМІНА ТЕМПЕРАТУРИ", "THAY ĐỔI NHIỆT ĐỘ"
        ),
        "weather_temp_change_alarm_toggle" to t(
            "Temperatuurwissel-alarm", "Temperature change alarm", "Alarma de cambio de temperatura", "Alarme de mudança de temperatura", "Temperaturwechsel-Alarm", "Alarme de changement de température", "Allarme variazione di temperatura", "기온 변화 알림", "温度变化提醒", "気温変化アラート",
            "Оповещение об изменении температуры", "تنبيه تغير درجة الحرارة", "तापमान परिवर्तन अलार्म", "Sıcaklık değişimi alarmı", "Alarm zmiany temperatury", "Alarm perubahan suhu", "Сповіщення про зміну температури", "Cảnh báo thay đổi nhiệt độ"
        ),
        "weather_temp_diff_desc" to t(
            "Verschil: {temp}°C", "Difference: {temp}°C", "Diferencia: {temp}°C", "Diferença: {temp}°C", "Unterschied: {temp}°C", "Différence : {temp}°C", "Differenza: {temp}°C", "차이: {temp}°C", "温差：{temp}°C", "差：{temp}°C",
            "Разница: {temp}°C", "الفرق: {temp}°م", "अंतर: {temp}°C", "Fark: {temp}°C", "Różnica: {temp}°C", "Selisih: {temp}°C", "Різниця: {temp}°C", "Chênh lệch: {temp}°C"
        ),
        "weather_day_before_forecast" to t(
            "Dag van tevoren (weersverwachting)", "Day before (forecast)", "Día anterior (pronóstico)", "Dia anterior (previsão)", "Tag zuvor (Vorhersage)", "Veille (prévisions)", "Giorno prima (previsione)", "전날 (예보 기준)", "前一天（天气预报）", "前日（天気予報）",
            "За день до (по прогнозу)", "اليوم السابق (توقعات الطقس)", "एक दिन पहले (पूर्वानुमान)", "Bir gün önce (tahmin)", "Dzień wcześniej (prognoza)", "Sehari sebelumnya (prakiraan)", "За день до (за прогнозом)", "Ngày trước đó (dự báo)"
        ),
        "weather_day_before_forecast_desc" to t(
            "Waarschuw op basis van de verwachting voor morgen, de avond ervoor", "Warn based on tomorrow's forecast, the evening before", "Avisar según el pronóstico de mañana, la noche anterior", "Avisar com base na previsão de amanhã, na noite anterior", "Warnen basierend auf der Vorhersage für morgen, am Abend zuvor", "Avertir en fonction des prévisions de demain, la veille au soir", "Avvisa in base alle previsioni di domani, la sera prima", "내일 예보를 기준으로 전날 저녁에 경고합니다", "根据明天的预报，在前一天晚上提醒", "明日の予報に基づき、前日の夜に警告します",
            "Предупреждать на основе прогноза на завтра, вечером накануне", "التحذير بناءً على توقعات الغد، في المساء السابق", "कल के पूर्वानुमान के आधार पर, एक रात पहले चेतावनी दें", "Yarının tahminine göre, bir önceki akşam uyar", "Ostrzegaj na podstawie prognozy na jutro, poprzedniego wieczoru", "Peringatkan berdasarkan prakiraan besok, pada malam sebelumnya", "Попереджати на основі прогнозу на завтра, увечері напередодні", "Cảnh báo dựa trên dự báo của ngày mai, vào tối hôm trước"
        ),
        "weather_same_day_fixed_time" to t(
            "Dag zelf, op bepaalde tijd", "Same day, at a specific time", "Mismo día, a una hora específica", "Mesmo dia, a uma hora específica", "Am selben Tag, zu einer bestimmten Uhrzeit", "Le jour même, à une heure précise", "Stesso giorno, a un orario specifico", "당일 지정된 시간에", "当天特定时间", "当日の指定時刻に",
            "В тот же день, в определённое время", "في نفس اليوم، في وقت محدد", "उसी दिन, एक निश्चित समय पर", "Aynı gün, belirli bir saatte", "Tego samego dnia, o określonej godzinie", "Hari yang sama, pada waktu tertentu", "У той самий день, у визначений час", "Cùng ngày, vào thời điểm cụ thể"
        ),
        "weather_same_day_fixed_time_desc" to t(
            "Waarschuw dezelfde dag nadat de verandering zichtbaar is, op een vast tijdstip", "Warn on the same day once the change is visible, at a fixed time", "Avisar el mismo día una vez que el cambio sea visible, a una hora fija", "Avisar no mesmo dia assim que a mudança for visível, a uma hora fixa", "Warnen am selben Tag, sobald die Änderung sichtbar ist, zu einer festen Uhrzeit", "Avertir le jour même dès que le changement est visible, à une heure fixe", "Avvisa lo stesso giorno non appena la variazione è visibile, a un orario fisso", "변화가 확인되는 즉시 같은 날 지정된 시간에 경고합니다", "在变化可见后于当天固定时间提醒", "変化が確認され次第、当日の決まった時刻に警告します",
            "Предупреждать в тот же день, как только изменение станет заметным, в фиксированное время", "التحذير في نفس اليوم بمجرد أن يصبح التغير ملحوظًا، في وقت ثابت", "उसी दिन जब परिवर्तन दिखाई दे, एक निश्चित समय पर चेतावनी दें", "Değişiklik görünür olduğunda aynı gün, sabit bir saatte uyar", "Ostrzegaj tego samego dnia, gdy zmiana stanie się widoczna, o stałej godzinie", "Peringatkan pada hari yang sama setelah perubahan terlihat, pada waktu tetap", "Попереджати того самого дня, щойно зміна стане помітною, у фіксований час", "Cảnh báo trong cùng ngày ngay khi thay đổi rõ ràng, vào một thời điểm cố định"
        ),
        "weather_warn_at_event" to t(
            "Waarschuw bij je agenda-item", "Warn at your calendar event", "Avisar en tu evento de calendario", "Avisar no teu evento da agenda", "Warnen bei deinem Kalendereintrag", "Avertir à votre événement du calendrier", "Avvisa al tuo evento in calendario", "일정 항목 시점에 경고", "在您的日程事件时提醒", "予定の時刻に警告",
            "Предупреждать в момент события календаря", "التحذير عند حدث التقويم الخاص بك", "आपके कैलेंडर इवेंट पर चेतावनी दें", "Takvim etkinliğinizde uyar", "Ostrzegaj podczas wydarzenia w kalendarzu", "Peringatkan saat acara kalender Anda", "Попереджати під час події вашого календаря", "Cảnh báo tại sự kiện lịch của bạn"
        ),
        "weather_warn_minutes_before" to t(
            "Waarschuw {min} minuten van tevoren", "Warn {min} minutes in advance", "Avisar {min} minutos antes", "Avisar {min} minutos antes", "Warnen {min} Minuten vorher", "Avertir {min} minutes à l'avance", "Avvisa {min} minuti prima", "{min}분 전에 경고", "提前 {min} 分钟提醒", "{min}分前に警告",
            "Предупреждать за {min} минут", "التحذير قبل {min} دقيقة", "{min} मिनट पहले चेतावनी दें", "{min} dakika önceden uyar", "Ostrzegaj {min} minut wcześniej", "Peringatkan {min} menit sebelumnya", "Попереджати за {min} хвилин", "Cảnh báo trước {min} phút"
        ),
        "weather_minutes_before_zero_note" to t(
            "{min} min van tevoren (0 = exact bij aanvang)", "{min} min in advance (0 = exactly at start)", "{min} min antes (0 = exactamente al inicio)", "{min} min antes (0 = exatamente no início)", "{min} Min. vorher (0 = genau zum Beginn)", "{min} min à l'avance (0 = exactement au début)", "{min} min prima (0 = esattamente all'inizio)", "{min}분 전 (0 = 시작 시점 정확히)", "提前 {min} 分钟（0 = 恰好在开始时）", "{min}分前（0＝開始時ちょうど）",
            "За {min} мин (0 = точно в начале)", "{min} دقيقة قبل (0 = بالضبط عند البدء)", "{min} मिनट पहले (0 = शुरुआत में ठीक)", "{min} dakika önceden (0 = tam başlangıçta)", "{min} min wcześniej (0 = dokładnie na początku)", "{min} menit sebelumnya (0 = tepat saat mulai)", "За {min} хв (0 = точно на початку)", "{min} phút trước (0 = đúng lúc bắt đầu)"
        ),
        "weather_temp_whole_day_desc_on" to t(
            "Vergelijkt de max. temperatuur van de hele dag met de dag erna, melding blijft rond het agenda-item", "Compares the whole day's max temperature with the next day, notification stays around the calendar event", "Compara la temperatura máxima de todo el día con la del día siguiente, la notificación se mantiene alrededor del evento de calendario", "Compara a temperatura máxima do dia inteiro com o dia seguinte, a notificação mantém-se em torno do evento da agenda", "Vergleicht die Tageshöchsttemperatur mit dem Folgetag, Benachrichtigung bleibt rund um den Kalendereintrag", "Compare la température maximale de toute la journée avec le lendemain, la notification reste autour de l'événement du calendrier", "Confronta la temperatura massima dell'intera giornata con quella del giorno successivo, la notifica resta intorno all'evento in calendario", "하루 전체의 최고 기온을 다음 날과 비교하며, 알림은 일정 항목 주변에 유지됩니다", "将全天最高气温与次日比较，提醒仍围绕日程事件发出", "1日全体の最高気温を翌日と比較し、通知は予定の前後に表示されます",
            "Сравнивает максимальную температуру за весь день со следующим днём, уведомление остаётся привязанным к событию календаря", "يقارن درجة الحرارة القصوى لليوم بأكمله باليوم التالي، ويبقى الإشعار حول حدث التقويم", "पूरे दिन के अधिकतम तापमान की अगले दिन से तुलना करता है, सूचना कैलेंडर इवेंट के आसपास ही रहती है", "Tüm günün maksimum sıcaklığını ertesi günle karşılaştırır, bildirim takvim etkinliği civarında kalır", "Porównuje maksymalną temperaturę całego dnia z następnym dniem, powiadomienie pozostaje wokół wydarzenia w kalendarzu", "Membandingkan suhu maksimum sepanjang hari dengan hari berikutnya, notifikasi tetap di sekitar acara kalender", "Порівнює максимальну температуру за весь день з наступним днем, сповіщення залишається прив'язаним до події календаря", "So sánh nhiệt độ tối đa cả ngày với ngày hôm sau, thông báo vẫn xoay quanh sự kiện lịch"
        ),
        "weather_temp_whole_day_desc_off" to t(
            "Vergelijkt puur het exacte tijdstip van het agenda-item met 24u later", "Compares purely the exact time of the calendar event with 24 hours later", "Compara exclusivamente la hora exacta del evento de calendario con 24 horas después", "Compara exclusivamente a hora exata do evento da agenda com 24h depois", "Vergleicht ausschließlich den genauen Zeitpunkt des Kalendereintrags mit 24 Std. später", "Compare uniquement l'heure exacte de l'événement du calendrier avec 24h plus tard", "Confronta esclusivamente l'orario esatto dell'evento in calendario con 24 ore dopo", "일정 항목의 정확한 시간을 24시간 후와만 비교합니다", "只比较日程事件的精确时间与24小时后", "予定の正確な時刻と24時間後のみを比較します",
            "Сравнивает исключительно точное время события календаря с временем через 24 часа", "يقارن فقط الوقت الدقيق لحدث التقويم بعد 24 ساعة", "केवल कैलेंडर इवेंट के सटीक समय की 24 घंटे बाद से तुलना करता है", "Yalnızca takvim etkinliğinin tam saatini 24 saat sonrasıyla karşılaştırır", "Porównuje wyłącznie dokładny moment wydarzenia w kalendarzu z 24 godzinami później", "Hanya membandingkan waktu tepat acara kalender dengan 24 jam kemudian", "Порівнює виключно точний час події календаря з часом через 24 години", "Chỉ so sánh đúng thời điểm sự kiện lịch với 24 giờ sau đó"
        ),
        "weather_choose_calendars_temp_change" to t(
            "Kies welke agenda's meetellen voor temperatuurwissel-meldingen", "Choose which calendars count for temperature-change notifications", "Elige qué calendarios cuentan para las notificaciones de cambio de temperatura", "Escolhe quais agendas contam para as notificações de mudança de temperatura", "Wähle, welche Kalender für Temperaturwechsel-Benachrichtigungen zählen", "Choisissez les calendriers qui comptent pour les notifications de changement de température", "Scegli quali calendari contano per le notifiche di variazione di temperatura", "기온 변화 알림에 반영할 일정을 선택하세요", "选择哪些日历计入温度变化提醒", "気温変化通知に反映するカレンダーを選択してください",
            "Выберите, какие календари учитываются для уведомлений об изменении температуры", "اختر التقويمات التي تُحتسب لإشعارات تغير درجة الحرارة", "चुनें कि कौन से कैलेंडर तापमान परिवर्तन सूचनाओं के लिए गिने जाएं", "Sıcaklık değişimi bildirimleri için hangi takvimlerin sayılacağını seçin", "Wybierz, które kalendarze liczą się do powiadomień o zmianie temperatury", "Pilih kalender mana yang dihitung untuk notifikasi perubahan suhu", "Виберіть, які календарі враховуються для сповіщень про зміну температури", "Chọn lịch nào được tính cho thông báo thay đổi nhiệt độ"
        ),
        "weather_section_general" to t(
            "ALGEMEEN", "GENERAL", "GENERAL", "GERAL", "ALLGEMEIN", "GÉNÉRAL", "GENERALE", "일반", "常规", "一般",
            "ОБЩИЕ", "عام", "सामान्य", "GENEL", "OGÓLNE", "UMUM", "ЗАГАЛЬНІ", "CHUNG"
        ),
        "weather_notifications_enable_toggle" to t(
            "Meldingen inschakelen", "Enable notifications", "Activar notificaciones", "Ativar notificações", "Benachrichtigungen aktivieren", "Activer les notifications", "Attiva notifiche", "알림 사용", "启用提醒", "通知を有効にする",
            "Включить уведомления", "تفعيل الإشعارات", "सूचनाएं सक्षम करें", "Bildirimleri etkinleştir", "Włącz powiadomienia", "Aktifkan notifikasi", "Увімкнути сповіщення", "Bật thông báo"
        ),
        "weather_no_permission_desc" to t(
            "Zonder toestemming voor meldingen kan er niets afgeleverd worden.", "Without notification permission, nothing can be delivered.", "Sin permiso de notificaciones no se puede entregar nada.", "Sem permissão de notificações, nada pode ser entregue.", "Ohne Benachrichtigungsberechtigung kann nichts zugestellt werden.", "Sans autorisation de notification, rien ne peut être délivré.", "Senza il permesso per le notifiche, non è possibile recapitare nulla.", "알림 권한이 없으면 아무것도 전달할 수 없습니다.", "没有通知权限将无法送达任何内容。", "通知の許可がないと何も配信できません。",
            "Без разрешения на уведомления ничего не может быть доставлено.", "بدون إذن الإشعارات، لا يمكن تسليم أي شيء.", "सूचना अनुमति के बिना कुछ भी वितरित नहीं किया जा सकता।", "Bildirim izni olmadan hiçbir şey iletilemez.", "Bez zgody na powiadomienia nic nie może zostać dostarczone.", "Tanpa izin notifikasi, tidak ada yang dapat dikirim.", "Без дозволу на сповіщення нічого не може бути доставлено.", "Không có quyền thông báo thì không thể gửi được gì."
        ),
        "weather_allow_notifications_button" to t(
            "Meldingen toestaan", "Allow notifications", "Permitir notificaciones", "Permitir notificações", "Benachrichtigungen zulassen", "Autoriser les notifications", "Consenti notifiche", "알림 허용", "允许通知", "通知を許可",
            "Разрешить уведомления", "السماح بالإشعارات", "सूचनाओं की अनुमति दें", "Bildirimlere izin ver", "Zezwól na powiadomienia", "Izinkan notifikasi", "Дозволити сповіщення", "Cho phép thông báo"
        ),
        "weather_link_to_calendar_toggle" to t(
            "Koppelen aan agenda-afspraken", "Link to calendar events", "Vincular a eventos de calendario", "Associar a eventos da agenda", "Mit Kalendereinträgen verknüpfen", "Lier aux événements du calendrier", "Collega agli eventi del calendario", "일정 이벤트와 연결", "关联到日历事件", "予定と連携",
            "Связать с событиями календаря", "الربط بأحداث التقويم", "कैलेंडर इवेंट से लिंक करें", "Takvim etkinlikleriyle bağla", "Powiąż z wydarzeniami w kalendarzu", "Tautkan ke acara kalender", "Пов'язати з подіями календаря", "Liên kết với sự kiện lịch"
        ),
        "weather_section_how_receive" to t(
            "HOE ONTVANGEN", "HOW TO RECEIVE", "CÓMO RECIBIR", "COMO RECEBER", "WIE EMPFANGEN", "COMMENT RECEVOIR", "COME RICEVERE", "수신 방법", "接收方式", "受信方法",
            "КАК ПОЛУЧАТЬ", "كيفية الاستلام", "कैसे प्राप्त करें", "NASIL ALINACAK", "JAK OTRZYMYWAĆ", "CARA MENERIMA", "ЯК ОТРИМУВАТИ", "CÁCH NHẬN"
        ),
        "weather_how_receive_desc" to t(
            "Kies hoe een weermelding binnenkomt - alle drie mogen tegelijk aan staan", "Choose how a weather notification arrives - all three may be on at the same time", "Elige cómo llega una notificación de clima - las tres pueden estar activas a la vez", "Escolhe como chega uma notificação de clima - as três podem estar ativas ao mesmo tempo", "Wähle, wie eine Wetterbenachrichtigung ankommt - alle drei können gleichzeitig aktiv sein", "Choisissez comment une notification météo arrive - les trois peuvent être actives en même temps", "Scegli come arriva una notifica meteo - tutte e tre possono essere attive contemporaneamente", "날씨 알림이 도착하는 방식을 선택하세요 - 세 가지 모두 동시에 켤 수 있습니다", "选择天气提醒的接收方式——三种可以同时开启", "天気通知の受け取り方を選択してください - 3つとも同時にオンにできます",
            "Выберите, как приходит уведомление о погоде — все три можно включить одновременно", "اختر كيفية وصول إشعار الطقس - يمكن تفعيل الثلاثة معًا", "चुनें कि मौसम सूचना कैसे आती है - तीनों एक साथ चालू हो सकते हैं", "Hava durumu bildiriminin nasıl geleceğini seçin - üçü aynı anda açık olabilir", "Wybierz, jak ma docierać powiadomienie o pogodzie - wszystkie trzy mogą być włączone jednocześnie", "Pilih cara notifikasi cuaca masuk - ketiganya bisa aktif bersamaan", "Виберіть, як надходитиме сповіщення про погоду — усі три можуть бути увімкнені одночасно", "Chọn cách thông báo thời tiết đến - cả ba có thể bật cùng lúc"
        ),
        "weather_notify_toggle" to t(
            "Melding", "Notification", "Notificación", "Notificação", "Benachrichtigung", "Notification", "Notifica", "알림", "通知", "通知",
            "Уведомление", "إشعار", "सूचना", "Bildirim", "Powiadomienie", "Notifikasi", "Сповіщення", "Thông báo"
        ),
        "weather_notify_toggle_desc" to t(
            "Gewone, stille pushmelding", "Regular, silent push notification", "Notificación push normal y silenciosa", "Notificação push normal e silenciosa", "Normale, stille Push-Benachrichtigung", "Notification push normale et silencieuse", "Notifica push normale e silenziosa", "일반적인 무음 푸시 알림", "普通的静音推送通知", "通常のサイレントプッシュ通知",
            "Обычное тихое push-уведомление", "إشعار دفع عادي وصامت", "सामान्य, मौन पुश सूचना", "Normal, sessiz push bildirimi", "Zwykłe, ciche powiadomienie push", "Notifikasi push biasa yang senyap", "Звичайне, тихе push-сповіщення", "Thông báo đẩy thông thường, im lặng"
        ),
        "weather_popup_toggle" to t(
            "Pop-up", "Pop-up", "Ventana emergente", "Pop-up", "Pop-up", "Pop-up", "Pop-up", "팝업", "弹窗", "ポップアップ",
            "Всплывающее окно", "نافذة منبثقة", "पॉप-अप", "Pop-up", "Wyskakujące okno", "Pop-up", "Спливаюче вікно", "Cửa sổ bật lên"
        ),
        "weather_popup_toggle_desc" to t(
            "Volledig scherm met geluid, zoals de wekker. Gebruikt op Android intern nog steeds een notificatie als drager, ook als \"{notify_label}\" hierboven uit staat.", "Full screen with sound, like the alarm clock. On Android this still internally uses a notification as a carrier, even if \"{notify_label}\" above is off.", "Pantalla completa con sonido, como la alarma. En Android esto sigue usando internamente una notificación como portador, incluso si \"{notify_label}\" arriba está desactivado.", "Ecrã inteiro com som, como o alarme. No Android isto ainda usa internamente uma notificação como portador, mesmo que \"{notify_label}\" acima esteja desativado.", "Vollbild mit Ton, wie der Wecker. Auf Android wird intern weiterhin eine Benachrichtigung als Träger verwendet, auch wenn \"{notify_label}\" oben ausgeschaltet ist.", "Plein écran avec son, comme le réveil. Sur Android, cela utilise toujours en interne une notification comme support, même si \"{notify_label}\" ci-dessus est désactivé.", "Schermo intero con suono, come la sveglia. Su Android questo utilizza ancora internamente una notifica come vettore, anche se \"{notify_label}\" sopra è disattivato.", "알람처럼 소리와 함께 전체 화면으로 표시됩니다. Android에서는 위의 \"{notify_label}\"이 꺼져 있어도 내부적으로 여전히 알림을 전달 수단으로 사용합니다.", "全屏并带声音，如同闹钟。在 Android 上，即使上面的\"{notify_label}\"已关闭，内部仍会使用通知作为载体。", "アラームのように音付きの全画面表示です。Androidでは上の「{notify_label}」がオフでも、内部的には引き続き通知が伝達手段として使われます。",
            "Полноэкранный режим со звуком, как будильник. На Android для этого всё равно внутренне используется уведомление как носитель, даже если \"{notify_label}\" выше выключено.", "شاشة كاملة مع صوت، مثل المنبه. على أندرويد، لا يزال هذا يستخدم داخليًا إشعارًا كوسيلة نقل، حتى لو كان \"{notify_label}\" أعلاه متوقفًا.", "अलार्म की तरह ध्वनि के साथ पूर्ण स्क्रीन। Android पर यह अभी भी आंतरिक रूप से एक सूचना का उपयोग वाहक के रूप में करता है, भले ही ऊपर \"{notify_label}\" बंद हो।", "Alarm gibi sesli tam ekran. Android'de bu, yukarıdaki \"{notify_label}\" kapalı olsa bile dahili olarak hala bir bildirimi taşıyıcı olarak kullanır.", "Pełny ekran z dźwiękiem, jak budzik. Na Androidzie nadal wewnętrznie wykorzystuje powiadomienie jako nośnik, nawet jeśli \"{notify_label}\" powyżej jest wyłączone.", "Layar penuh dengan suara, seperti alarm. Di Android ini masih menggunakan notifikasi secara internal sebagai pembawa, meskipun \"{notify_label}\" di atas dimatikan.", "Повноекранний режим зі звуком, як будильник. На Android це все одно внутрішньо використовує сповіщення як носій, навіть якщо \"{notify_label}\" вище вимкнено.", "Toàn màn hình có âm thanh, giống báo thức. Trên Android, tính năng này vẫn sử dụng nội bộ một thông báo làm phương tiện truyền tải, ngay cả khi \"{notify_label}\" ở trên đang tắt."
        ),
        "weather_speak_toggle" to t(
            "Uitspreken", "Speak aloud", "Leer en voz alta", "Falar em voz alta", "Vorlesen", "Énoncer à voix haute", "Pronuncia ad alta voce", "음성 안내", "语音播报", "読み上げ",
            "Произносить вслух", "النطق بصوت عالٍ", "ज़ोर से बोलें", "Sesli okuma", "Odczytaj na głos", "Ucapkan", "Озвучувати", "Đọc to"
        ),
        "weather_speak_toggle_desc" to t(
            "Spreekt de melding hardop uit, via de externe speaker (Instellingen > Home Assistant) of anders via de telefoon zelf.", "Speaks the notification out loud, via the external speaker (Settings > Home Assistant) or otherwise via the phone itself.", "Lee la notificación en voz alta, a través del altavoz externo (Ajustes > Home Assistant) o si no, a través del propio teléfono.", "Diz a notificação em voz alta, através do altifalante externo (Definições > Home Assistant) ou, caso contrário, através do próprio telefone.", "Liest die Benachrichtigung laut vor, über den externen Lautsprecher (Einstellungen > Home Assistant) oder sonst über das Telefon selbst.", "Énonce la notification à voix haute, via le haut-parleur externe (Paramètres > Home Assistant) ou sinon via le téléphone lui-même.", "Pronuncia la notifica ad alta voce, tramite l'altoparlante esterno (Impostazioni > Home Assistant) o altrimenti tramite il telefono stesso.", "외부 스피커(설정 > Home Assistant)를 통해 알림을 소리 내어 읽거나, 그렇지 않으면 휴대폰 자체를 통해 읽습니다.", "通过外部扬声器（设置 > Home Assistant）大声朗读提醒，否则通过手机本身朗读。", "外部スピーカー（設定＞Home Assistant）経由で通知を読み上げます。それ以外の場合は端末自体で読み上げます。",
            "Произносит уведомление вслух через внешний динамик (Настройки > Home Assistant) или иначе через сам телефон.", "ينطق الإشعار بصوت عالٍ عبر مكبر الصوت الخارجي (الإعدادات > Home Assistant) أو، بخلاف ذلك، عبر الهاتف نفسه.", "बाहरी स्पीकर (सेटिंग्स > Home Assistant) के माध्यम से सूचना को ज़ोर से बोलता है, अन्यथा फ़ोन के माध्यम से।", "Bildirimi harici hoparlör (Ayarlar > Home Assistant) üzerinden veya aksi halde telefonun kendisi üzerinden sesli okur.", "Odczytuje powiadomienie na głos przez zewnętrzny głośnik (Ustawienia > Home Assistant) lub w przeciwnym razie przez sam telefon.", "Mengucapkan notifikasi dengan lantang, melalui speaker eksternal (Pengaturan > Home Assistant) atau melalui ponsel itu sendiri.", "Промовляє сповіщення вголос через зовнішній динамік (Налаштування > Home Assistant) або інакше через сам телефон.", "Đọc to thông báo qua loa ngoài (Cài đặt > Home Assistant) hoặc nếu không thì qua chính điện thoại."
        ),
        "weather_section_home_assistant" to t(
            "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT",
            "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT", "HOME ASSISTANT"
        ),
        "weather_ha_link_desc" to t(
            "Koppel Home Assistant om weeralarmen ook op een speaker af te spelen of uit te spreken.", "Link Home Assistant to also play or speak weather alarms on a speaker.", "Vincula Home Assistant para reproducir o leer también las alarmas de clima en un altavoz.", "Associa o Home Assistant para também reproduzir ou dizer os alarmes de clima num altifalante.", "Verknüpfe Home Assistant, um Wetteralarme auch über einen Lautsprecher abzuspielen oder vorzulesen.", "Liez Home Assistant pour également jouer ou énoncer les alarmes météo sur un haut-parleur.", "Collega Home Assistant per riprodurre o pronunciare gli allarmi meteo anche su un altoparlante.", "스피커에서도 날씨 알람을 재생하거나 음성으로 안내하려면 Home Assistant를 연결하세요.", "关联 Home Assistant，以便同时在扬声器上播放或朗读天气警报。", "Home Assistantと連携すると、スピーカーでも天気アラームを再生・読み上げできます。",
            "Свяжите Home Assistant, чтобы также воспроизводить или произносить погодные оповещения через динамик.", "اربط Home Assistant لتشغيل أو نطق تنبيهات الطقس أيضًا عبر مكبر صوت.", "स्पीकर पर भी मौसम अलार्म चलाने या बोलने के लिए Home Assistant को लिंक करें।", "Hava durumu alarmlarını bir hoparlörde de çalmak veya seslendirmek için Home Assistant'ı bağlayın.", "Połącz Home Assistant, aby odtwarzać lub odczytywać alarmy pogodowe także na głośniku.", "Tautkan Home Assistant untuk juga memutar atau mengucapkan alarm cuaca di speaker.", "Пов'яжіть Home Assistant, щоб також відтворювати або озвучувати погодні сповіщення через динамік.", "Liên kết Home Assistant để cũng phát hoặc đọc cảnh báo thời tiết qua loa."
        ),
        "weather_section_temp_unit" to t(
            "TEMPERATUUREENHEID", "TEMPERATURE UNIT", "UNIDAD DE TEMPERATURA", "UNIDADE DE TEMPERATURA", "TEMPERATUREINHEIT", "UNITÉ DE TEMPÉRATURE", "UNITÀ DI TEMPERATURA", "온도 단위", "温度单位", "気温単位",
            "ЕДИНИЦА ТЕМПЕРАТУРЫ", "وحدة درجة الحرارة", "तापमान इकाई", "SICAKLIK BİRİMİ", "JEDNOSTKA TEMPERATURY", "SATUAN SUHU", "ОДИНИЦЯ ТЕМПЕРАТУРИ", "ĐƠN VỊ NHIỆT ĐỘ"
        ),
        "weather_temp_unit_desc" to t(
            "Geldt voor de getoonde temperaturen op het weertabblad (nu, vandaag, komende dagen). Drempels bij Extra weersomstandigheden blijven in Celsius.", "Applies to the temperatures shown on the weather tab (now, today, upcoming days). Thresholds under Extra weather conditions remain in Celsius.", "Se aplica a las temperaturas mostradas en la pestaña de clima (ahora, hoy, próximos días). Los umbrales en Condiciones meteorológicas adicionales permanecen en Celsius.", "Aplica-se às temperaturas mostradas no separador de clima (agora, hoje, próximos dias). Os limites em Condições meteorológicas extra permanecem em Celsius.", "Gilt für die im Wetter-Tab angezeigten Temperaturen (jetzt, heute, kommende Tage). Schwellenwerte bei Zusätzlichen Wetterbedingungen bleiben in Celsius.", "S'applique aux températures affichées dans l'onglet météo (maintenant, aujourd'hui, jours à venir). Les seuils dans Conditions météo supplémentaires restent en Celsius.", "Si applica alle temperature mostrate nella scheda meteo (ora, oggi, prossimi giorni). Le soglie in Condizioni meteo aggiuntive restano in Celsius.", "날씨 탭에 표시되는 온도(현재, 오늘, 향후)에 적용됩니다. 추가 기상 조건의 임계값은 섭씨로 유지됩니다.", "适用于天气标签页显示的温度（现在、今天、未来几天）。额外天气状况中的阈值仍以摄氏度为准。", "天気タブに表示される気温（現在、今日、今後の日）に適用されます。追加の気象条件のしきい値は摂氏のままです。",
            "Применяется к температурам, показанным на вкладке погоды (сейчас, сегодня, ближайшие дни). Пороги в разделе Дополнительные погодные условия остаются в градусах Цельсия.", "ينطبق على درجات الحرارة المعروضة في تبويب الطقس (الآن، اليوم، الأيام القادمة). تبقى الحدود في الظروف الجوية الإضافية بالمئوية.", "मौसम टैब पर दिखाए गए तापमान (अभी, आज, आने वाले दिन) पर लागू होता है। अतिरिक्त मौसम स्थितियों की सीमाएं सेल्सियस में ही रहती हैं।", "Hava durumu sekmesinde gösterilen sıcaklıklar için geçerlidir (şimdi, bugün, gelecek günler). Ek hava koşullarındaki eşikler Celsius olarak kalır.", "Dotyczy temperatur pokazywanych na karcie pogody (teraz, dziś, nadchodzące dni). Progi w Dodatkowych warunkach pogodowych pozostają w stopniach Celsjusza.", "Berlaku untuk suhu yang ditampilkan di tab cuaca (sekarang, hari ini, hari mendatang). Ambang batas di Kondisi cuaca tambahan tetap dalam Celsius.", "Застосовується до температур, показаних на вкладці погоди (зараз, сьогодні, найближчі дні). Пороги в розділі Додаткові погодні умови залишаються в градусах Цельсія.", "Áp dụng cho nhiệt độ hiển thị trên tab thời tiết (hiện tại, hôm nay, những ngày sắp tới). Ngưỡng trong Điều kiện thời tiết bổ sung vẫn giữ ở độ C."
        ),
        "weather_celsius_label" to t(
            "Celsius (°C)", "Celsius (°C)", "Celsius (°C)", "Celsius (°C)", "Celsius (°C)", "Celsius (°C)", "Celsius (°C)", "섭씨 (°C)", "摄氏度（°C）", "摂氏（°C）",
            "Цельсий (°C)", "مئوية (°م)", "सेल्सियस (°C)", "Celsius (°C)", "Celsjusz (°C)", "Celsius (°C)", "Цельсій (°C)", "Celsius (°C)"
        ),
        "weather_fahrenheit_label" to t(
            "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "화씨 (°F)", "华氏度（°F）", "華氏（°F）",
            "Фаренгейт (°F)", "فهرنهايت (°ف)", "फ़ारेनहाइट (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Fahrenheit (°F)", "Фаренгейт (°F)", "Fahrenheit (°F)"
        ),
        "weather_section_today_tomorrow" to t(
            "VANDAAG NAAR MORGEN", "TODAY TO TOMORROW", "DE HOY A MAÑANA", "DE HOJE PARA AMANHÃ", "HEUTE ZU MORGEN", "D'AUJOURD'HUI À DEMAIN", "DA OGGI A DOMANI", "오늘에서 내일로", "从今天到明天", "今日から明日へ",
            "ОТ СЕГОДНЯ К ЗАВТРА", "من اليوم إلى الغد", "आज से कल तक", "BUGÜNDEN YARINA", "OD DZIŚ DO JUTRA", "HARI INI KE BESOK", "ВІД СЬОГОДНІ ДО ЗАВТРА", "TỪ HÔM NAY SANG NGÀY MAI"
        ),
        "weather_evening_switch_desc" to t(
            "Als er geen waarschuwing actief is, toont het weertabblad 's avonds vanaf dit tijdstip de verwachting voor morgen in plaats van het huidige weer van vandaag.", "If no warning is active, from this time in the evening the weather tab shows tomorrow's forecast instead of today's current weather.", "Si no hay ninguna advertencia activa, a partir de esta hora por la noche la pestaña de clima muestra el pronóstico de mañana en lugar del clima actual de hoy.", "Se não houver nenhum aviso ativo, a partir desta hora à noite o separador de clima mostra a previsão de amanhã em vez do clima atual de hoje.", "Wenn keine Warnung aktiv ist, zeigt der Wetter-Tab ab dieser Uhrzeit am Abend die Vorhersage für morgen anstelle des aktuellen Wetters von heute.", "Si aucun avertissement n'est actif, à partir de cette heure le soir, l'onglet météo affiche les prévisions de demain au lieu de la météo actuelle d'aujourd'hui.", "Se non c'è alcun avviso attivo, da questo orario della sera la scheda meteo mostra le previsioni di domani invece del meteo attuale di oggi.", "경고가 활성화되어 있지 않으면, 이 시간 이후 저녁부터 날씨 탭에 오늘의 현재 날씨 대신 내일의 예보가 표시됩니다.", "如果没有活跃的警告，从当天晚上此时间起，天气标签页将显示明天的预报，而不是今天的实时天气。", "警告が有効でない場合、この時刻以降の夜間は、天気タブに今日の現在の天気の代わりに明日の予報が表示されます。",
            "Если предупреждение не активно, начиная с этого времени вечером вкладка погоды показывает прогноз на завтра вместо текущей погоды сегодня.", "إذا لم يكن هناك أي تحذير نشط، ابتداءً من هذا الوقت في المساء، يعرض تبويب الطقس توقعات الغد بدلاً من طقس اليوم الحالي.", "यदि कोई चेतावनी सक्रिय नहीं है, तो शाम को इस समय से मौसम टैब आज के वर्तमान मौसम के बजाय कल का पूर्वानुमान दिखाता है।", "Aktif bir uyarı yoksa, akşam bu saatten itibaren hava durumu sekmesi bugünün mevcut havası yerine yarının tahminini gösterir.", "Jeśli żadne ostrzeżenie nie jest aktywne, od tej godziny wieczorem karta pogody pokazuje prognozę na jutro zamiast aktualnej pogody dzisiaj.", "Jika tidak ada peringatan aktif, mulai waktu ini pada malam hari tab cuaca menampilkan prakiraan besok, bukan cuaca hari ini saat ini.", "Якщо жодне попередження не активне, починаючи з цього часу ввечері вкладка погоди показує прогноз на завтра замість поточної погоди сьогодні.", "Nếu không có cảnh báo nào đang hoạt động, từ thời điểm này vào buổi tối, tab thời tiết sẽ hiển thị dự báo cho ngày mai thay vì thời tiết hiện tại của hôm nay."
        ),
        "weather_ha_page_desc" to t(
            "Koppel de app aan Home Assistant. Later kun je hiermee weermeldingen via een HA-speaker afspelen en widgets met HA-sensoren tonen.", "Link the app to Home Assistant. Later you can use this to play weather notifications through an HA speaker and show widgets with HA sensors.", "Vincula la app a Home Assistant. Más adelante podrás reproducir notificaciones de clima a través de un altavoz HA y mostrar widgets con sensores HA.", "Associa a app ao Home Assistant. Mais tarde podes usar isto para reproduzir notificações de clima através de um altifalante HA e mostrar widgets com sensores HA.", "Verknüpfe die App mit Home Assistant. Später kannst du damit Wetterbenachrichtigungen über einen HA-Lautsprecher abspielen und Widgets mit HA-Sensoren anzeigen.", "Liez l'application à Home Assistant. Plus tard, vous pourrez ainsi lire les notifications météo via un haut-parleur HA et afficher des widgets avec des capteurs HA.", "Collega l'app a Home Assistant. In seguito potrai usarlo per riprodurre notifiche meteo tramite un altoparlante HA e mostrare widget con sensori HA.", "앱을 Home Assistant에 연결하세요. 나중에 이를 통해 HA 스피커로 날씨 알림을 재생하고 HA 센서가 포함된 위젯을 표시할 수 있습니다.", "将应用与 Home Assistant 关联。之后您可以借此通过 HA 扬声器播放天气提醒，并显示带有 HA 传感器的小组件。", "アプリをHome Assistantと連携させます。後でこれを使ってHAスピーカーで天気通知を再生したり、HAセンサー付きウィジェットを表示したりできます。",
            "Свяжите приложение с Home Assistant. Позже вы сможете воспроизводить погодные уведомления через динамик HA и показывать виджеты с датчиками HA.", "اربط التطبيق بـ Home Assistant. لاحقًا يمكنك استخدام ذلك لتشغيل إشعارات الطقس عبر مكبر صوت HA وعرض أدوات بأجهزة استشعار HA.", "ऐप को Home Assistant से लिंक करें। बाद में आप इसका उपयोग HA स्पीकर के माध्यम से मौसम सूचनाएं चलाने और HA सेंसर वाले विजेट दिखाने के लिए कर सकते हैं।", "Uygulamayı Home Assistant'a bağlayın. Daha sonra bunu bir HA hoparlörü üzerinden hava durumu bildirimlerini çalmak ve HA sensörlü widget'lar göstermek için kullanabilirsiniz.", "Połącz aplikację z Home Assistant. Później będziesz mógł dzięki temu odtwarzać powiadomienia pogodowe przez głośnik HA i wyświetlać widżety z czujnikami HA.", "Tautkan aplikasi ke Home Assistant. Nantinya Anda dapat menggunakannya untuk memutar notifikasi cuaca melalui speaker HA dan menampilkan widget dengan sensor HA.", "Пов'яжіть додаток з Home Assistant. Пізніше ви зможете відтворювати погодні сповіщення через динамік HA та показувати віджети з датчиками HA.", "Liên kết ứng dụng với Home Assistant. Sau này bạn có thể dùng để phát thông báo thời tiết qua loa HA và hiển thị tiện ích với cảm biến HA."
        ),
        "weather_ha_qr_not_recognized" to t(
            "QR-code niet herkend als AgendaAlarm-koppelcode", "QR code not recognized as a CalendarAlarm pairing code", "Código QR no reconocido como código de vinculación de AgendaAlarm", "Código QR não reconhecido como código de emparelhamento AgendaAlarm", "QR-Code nicht als AgendaAlarm-Kopplungscode erkannt", "Code QR non reconnu comme code de jumelage AgendaAlarm", "Codice QR non riconosciuto come codice di associazione AgendaAlarm", "QR 코드가 AgendaAlarm 페어링 코드로 인식되지 않았습니다", "未将二维码识别为 AgendaAlarm 配对码", "QRコードがAgendaAlarmのペアリングコードとして認識されませんでした",
            "QR-код не распознан как код сопряжения AgendaAlarm", "لم يتم التعرف على رمز QR كرمز اقتران AgendaAlarm", "QR कोड को AgendaAlarm पेयरिंग कोड के रूप में पहचाना नहीं गया", "QR kodu AgendaAlarm eşleştirme kodu olarak tanınmadı", "Kod QR nie został rozpoznany jako kod parowania AgendaAlarm", "Kode QR tidak dikenali sebagai kode pemasangan AgendaAlarm", "QR-код не розпізнано як код спарювання AgendaAlarm", "Mã QR không được nhận dạng là mã ghép nối AgendaAlarm"
        ),
        "weather_ha_scan_prompt" to t(
            "Scan de QR-code in Home Assistant", "Scan the QR code in Home Assistant", "Escanea el código QR en Home Assistant", "Digitaliza o código QR no Home Assistant", "Scanne den QR-Code in Home Assistant", "Scannez le code QR dans Home Assistant", "Scansiona il codice QR in Home Assistant", "Home Assistant에서 QR 코드를 스캔하세요", "扫描 Home Assistant 中的二维码", "Home AssistantのQRコードをスキャンしてください",
            "Отсканируйте QR-код в Home Assistant", "امسح رمز QR في Home Assistant", "Home Assistant में QR कोड स्कैन करें", "Home Assistant'taki QR kodunu tarayın", "Zeskanuj kod QR w Home Assistant", "Pindai kode QR di Home Assistant", "Відскануйте QR-код у Home Assistant", "Quét mã QR trong Home Assistant"
        ),
        "weather_ha_camera_permission_needed" to t(
            "Camera-toestemming nodig om te scannen", "Camera permission needed to scan", "Se necesita permiso de cámara para escanear", "É necessária permissão de câmara para digitalizar", "Kameraberechtigung zum Scannen erforderlich", "Autorisation de la caméra requise pour scanner", "Permesso della fotocamera necessario per scansionare", "스캔하려면 카메라 권한이 필요합니다", "扫描需要相机权限", "スキャンするにはカメラの許可が必要です",
            "Для сканирования требуется разрешение на использование камеры", "إذن الكاميرا مطلوب للمسح", "स्कैन करने के लिए कैमरा अनुमति आवश्यक है", "Taramak için kamera izni gerekli", "Do skanowania wymagane jest uprawnienie do aparatu", "Izin kamera diperlukan untuk memindai", "Для сканування потрібен дозвіл на використання камери", "Cần quyền truy cập camera để quét"
        ),
        "weather_ha_pair_title" to t(
            "Koppelen (QR/code)", "Pair (QR/code)", "Vincular (QR/código)", "Emparelhar (QR/código)", "Koppeln (QR/Code)", "Jumeler (QR/code)", "Associa (QR/codice)", "페어링(QR/코드)", "配对（二维码/代码）", "ペアリング（QR／コード）",
            "Сопряжение (QR/код)", "اقتران (QR/رمز)", "पेयर करें (QR/कोड)", "Eşleştir (QR/kod)", "Paruj (QR/kod)", "Pasangkan (QR/kode)", "Спарювання (QR/код)", "Ghép nối (QR/mã)"
        ),
        "weather_ha_pair_desc" to t(
            "Scan een QR-code of typ een setup-code over vanuit Home Assistant - geen token kopieren nodig", "Scan a QR code or type a setup code from Home Assistant - no token copying needed", "Escanea un código QR o escribe un código de configuración desde Home Assistant - no es necesario copiar ningún token", "Digitaliza um código QR ou escreve um código de configuração a partir do Home Assistant - não é necessário copiar nenhum token", "Scanne einen QR-Code oder gib einen Einrichtungscode aus Home Assistant ein - kein Token-Kopieren nötig", "Scannez un code QR ou saisissez un code de configuration depuis Home Assistant - aucune copie de jeton nécessaire", "Scansiona un codice QR o digita un codice di configurazione da Home Assistant - nessuna copia del token necessaria", "QR 코드를 스캔하거나 Home Assistant에서 설정 코드를 입력하세요 - 토큰을 복사할 필요가 없습니다", "扫描二维码或从 Home Assistant 输入设置代码——无需复制令牌", "QRコードをスキャンするか、Home Assistantから設定コードを入力してください - トークンをコピーする必要はありません",
            "Отсканируйте QR-код или введите код настройки из Home Assistant — копировать токен не требуется", "امسح رمز QR أو اكتب رمز إعداد من Home Assistant - لا حاجة لنسخ الرمز المميز", "QR कोड स्कैन करें या Home Assistant से सेटअप कोड टाइप करें - टोकन कॉपी करने की आवश्यकता नहीं", "Bir QR kodu tarayın veya Home Assistant'tan bir kurulum kodu girin - token kopyalamaya gerek yok", "Zeskanuj kod QR lub wpisz kod konfiguracyjny z Home Assistant - kopiowanie tokena nie jest potrzebne", "Pindai kode QR atau ketik kode penyiapan dari Home Assistant - tidak perlu menyalin token", "Відскануйте QR-код або введіть код налаштування з Home Assistant — копіювати токен не потрібно", "Quét mã QR hoặc nhập mã thiết lập từ Home Assistant - không cần sao chép token"
        ),
        "weather_ha_settings_copied" to t(
            "Settings gekopieerd naar klembord", "Settings copied to clipboard", "Ajustes copiados al portapapeles", "Definições copiadas para a área de transferência", "Einstellungen in die Zwischenablage kopiert", "Paramètres copiés dans le presse-papiers", "Impostazioni copiate negli appunti", "설정이 클립보드에 복사되었습니다", "设置已复制到剪贴板", "設定をクリップボードにコピーしました",
            "Настройки скопированы в буфер обмена", "تم نسخ الإعدادات إلى الحافظة", "सेटिंग्स क्लिपबोर्ड पर कॉपी की गईं", "Ayarlar panoya kopyalandı", "Ustawienia skopiowane do schowka", "Pengaturan disalin ke clipboard", "Налаштування скопійовано в буфер обміну", "Đã sao chép cài đặt vào clipboard"
        ),
        "weather_section_tts" to t(
            "WEERALARM UITSPREKEN", "SPEAK WEATHER ALARM", "LEER ALARMA DE CLIMA EN VOZ ALTA", "DIZER ALARME DE CLIMA EM VOZ ALTA", "WETTERALARM VORLESEN", "ÉNONCER L'ALARME MÉTÉO", "PRONUNCIA ALLARME METEO", "날씨 알람 음성 안내", "朗读天气警报", "天気アラームを読み上げ",
            "ПРОИЗНОСИТЬ ПОГОДНОЕ ОПОВЕЩЕНИЕ", "نطق تنبيه الطقس", "मौसम अलार्म बोलें", "HAVA DURUMU ALARMINI SESLENDİR", "ODCZYTAJ ALARM POGODOWY", "UCAPKAN ALARM CUACA", "ОЗВУЧУВАТИ ПОГОДНЕ СПОВІЩЕННЯ", "ĐỌC TO CẢNH BÁO THỜI TIẾT"
        ),
        "weather_tts_desc" to t(
            "Laat de speaker hierboven het weeralarm ook hardop uitspreken. De telefoon genereert dit zelf (via de ingebouwde tekst-naar-spraak) en stuurt het naar de speaker - geen extra HA-installatie nodig.", "Have the speaker above also speak the weather alarm aloud. The phone generates this itself (via its built-in text-to-speech) and sends it to the speaker - no extra HA setup needed.", "Haz que el altavoz de arriba también lea la alarma de clima en voz alta. El teléfono la genera él mismo (mediante su texto a voz integrado) y la envía al altavoz - no se necesita ninguna configuración adicional en HA.", "Faz com que o altifalante acima também diga o alarme de clima em voz alta. O telemóvel gera isto por si próprio (através do texto para voz integrado) e envia-o para o altifalante - não é necessária configuração adicional no HA.", "Lässt den obigen Lautsprecher den Wetteralarm auch laut vorlesen. Das Telefon erzeugt dies selbst (über die eingebaute Sprachausgabe) und sendet es an den Lautsprecher - keine zusätzliche HA-Einrichtung nötig.", "Faites en sorte que le haut-parleur ci-dessus énonce également l'alarme météo à voix haute. Le téléphone la génère lui-même (via sa synthèse vocale intégrée) et l'envoie au haut-parleur - aucune configuration HA supplémentaire nécessaire.", "Fai in modo che l'altoparlante sopra pronunci anche l'allarme meteo ad alta voce. Il telefono la genera da solo (tramite la sintesi vocale integrata) e la invia all'altoparlante - nessuna configurazione HA aggiuntiva necessaria.", "위의 스피커가 날씨 알람도 소리 내어 읽도록 합니다. 전화기가 내장된 음성 합성 기능으로 직접 생성하여 스피커로 전송합니다 - 추가 HA 설정이 필요 없습니다.", "让上面的扬声器也大声朗读天气警报。手机会自行生成语音（通过内置的文字转语音功能）并发送到扬声器——无需额外的 HA 设置。", "上のスピーカーが天気アラームも読み上げるようにします。スマートフォンが内蔵の音声合成で自動生成し、スピーカーに送信します - 追加のHA設定は不要です。",
            "Пусть динамик выше также произносит погодное оповещение вслух. Телефон генерирует это сам (через встроенный синтез речи) и отправляет на динамик - дополнительная настройка HA не требуется.", "اجعل مكبر الصوت أعلاه ينطق أيضًا تنبيه الطقس بصوت عالٍ. يقوم الهاتف بإنشاء ذلك بنفسه (عبر تحويل النص إلى كلام المدمج) ويرسله إلى مكبر الصوت - لا حاجة لأي إعداد إضافي في HA.", "ऊपर दिए गए स्पीकर से मौसम अलार्म को भी ज़ोर से बुलवाएं। फ़ोन इसे स्वयं जनरेट करता है (अंतर्निहित टेक्स्ट-टू-स्पीच के माध्यम से) और इसे स्पीकर पर भेजता है - किसी अतिरिक्त HA सेटअप की आवश्यकता नहीं है।", "Yukarıdaki hoparlörün hava durumu alarmını da sesli okumasını sağlayın. Telefon bunu kendisi oluşturur (dahili metinden sese özelliğiyle) ve hoparlöre gönderir - ekstra HA kurulumu gerekmez.", "Spraw, aby głośnik powyżej również odczytywał na głos alarm pogodowy. Telefon generuje to samodzielnie (poprzez wbudowaną syntezę mowy) i wysyła do głośnika - nie jest potrzebna dodatkowa konfiguracja HA.", "Buat speaker di atas juga mengucapkan alarm cuaca dengan lantang. Ponsel menghasilkannya sendiri (melalui text-to-speech bawaan) dan mengirimkannya ke speaker - tidak perlu pengaturan HA tambahan.", "Нехай динамік вище також озвучує погодне сповіщення вголос. Телефон генерує це самостійно (через вбудований синтез мовлення) і надсилає на динамік - додаткове налаштування HA не потрібне.", "Cho loa ở trên cũng đọc to cảnh báo thời tiết. Điện thoại tự tạo giọng nói này (thông qua chuyển văn bản thành giọng nói tích hợp sẵn) và gửi đến loa - không cần cài đặt HA bổ sung."
        ),
        "weather_tts_speaker_toggle" to t(
            "Uitspreken op speaker", "Speak on speaker", "Leer en el altavoz", "Dizer no altifalante", "Auf Lautsprecher vorlesen", "Énoncer sur le haut-parleur", "Pronuncia sull'altoparlante", "스피커에서 음성 안내", "通过扬声器朗读", "スピーカーで読み上げ",
            "Произносить через динамик", "النطق عبر مكبر الصوت", "स्पीकर पर बोलें", "Hoparlörde seslendir", "Odczytaj na głośniku", "Ucapkan di speaker", "Озвучувати через динамік", "Đọc qua loa"
        ),
        "weather_tts_select_speaker_first" to t(
            "Kies hierboven eerst een speaker.", "First choose a speaker above.", "Primero elige un altavoz arriba.", "Primeiro escolhe um altifalante acima.", "Wähle oben zuerst einen Lautsprecher.", "Choisissez d'abord un haut-parleur ci-dessus.", "Scegli prima un altoparlante sopra.", "먼저 위에서 스피커를 선택하세요.", "请先在上方选择一个扬声器。", "まず上でスピーカーを選択してください。",
            "Сначала выберите динамик выше.", "اختر أولاً مكبر صوت أعلاه.", "पहले ऊपर एक स्पीकर चुनें।", "Önce yukarıdan bir hoparlör seçin.", "Najpierw wybierz głośnik powyżej.", "Pilih speaker di atas terlebih dahulu.", "Спочатку виберіть динамік вище.", "Trước tiên hãy chọn loa ở trên."
        ),
        "weather_widgets_page_desc" to t(
            "Toon tot 2 widgets met eigen Home Assistant-sensoren boven het weer op de homepage. Bij één widget staat die in het midden, bij twee ernaast elkaar. Kies per widget tot 3 entiteiten uit je Entiteiten-lijst; laat de naam leeg om de naam van de entiteit zelf te gebruiken.", "Show up to 2 widgets with your own Home Assistant sensors above the weather on the home page. With one widget it's centered, with two they sit side by side. Choose up to 3 entities per widget from your Entities list; leave the name blank to use the entity's own name.", "Muestra hasta 2 widgets con tus propios sensores de Home Assistant encima del clima en la página de inicio. Con un widget queda centrado, con dos quedan uno junto al otro. Elige hasta 3 entidades por widget de tu lista de entidades; deja el nombre vacío para usar el nombre de la propia entidad.", "Mostra até 2 widgets com os teus próprios sensores Home Assistant acima do clima na página inicial. Com um widget fica centrado, com dois ficam lado a lado. Escolhe até 3 entidades por widget da tua lista de Entidades; deixa o nome em branco para usar o nome da própria entidade.", "Zeige bis zu 2 Widgets mit eigenen Home Assistant-Sensoren über dem Wetter auf der Startseite. Bei einem Widget steht es mittig, bei zwei nebeneinander. Wähle pro Widget bis zu 3 Entitäten aus deiner Entitätenliste; lasse den Namen leer, um den Namen der Entität selbst zu verwenden.", "Affichez jusqu'à 2 widgets avec vos propres capteurs Home Assistant au-dessus de la météo sur la page d'accueil. Avec un widget, il est centré, avec deux, ils sont côte à côte. Choisissez jusqu'à 3 entités par widget dans votre liste d'entités ; laissez le nom vide pour utiliser le nom de l'entité elle-même.", "Mostra fino a 2 widget con i tuoi sensori Home Assistant sopra il meteo nella home page. Con un widget è centrato, con due sono affiancati. Scegli fino a 3 entità per widget dalla tua lista Entità; lascia il nome vuoto per usare il nome dell'entità stessa.", "홈페이지의 날씨 위에 자신의 Home Assistant 센서가 포함된 위젯을 최대 2개까지 표시합니다. 위젯이 하나면 가운데에, 두 개면 나란히 배치됩니다. 위젯마다 엔티티 목록에서 최대 3개의 엔티티를 선택하세요. 이름을 비워두면 엔티티 자체의 이름이 사용됩니다.", "在主页天气上方最多显示 2 个带有您自己 Home Assistant 传感器的小组件。只有一个小组件时居中显示，两个时并排显示。每个小组件可从您的实体列表中选择最多 3 个实体；将名称留空以使用实体本身的名称。", "ホームページの天気の上に、最大2つのHome Assistantセンサー付きウィジェットを表示します。ウィジェットが1つの場合は中央に、2つの場合は横並びに配置されます。ウィジェットごとに、エンティティ一覧から最大3つのエンティティを選択できます。名前を空欄にすると、エンティティ自体の名前が使われます。",
            "Показывайте до 2 виджетов с собственными датчиками Home Assistant над погодой на главной странице. При одном виджете он по центру, при двух — рядом. Выберите для каждого виджета до 3 сущностей из списка сущностей; оставьте имя пустым, чтобы использовать собственное имя сущности.", "اعرض ما يصل إلى أداتين مع مستشعرات Home Assistant الخاصة بك فوق الطقس في الصفحة الرئيسية. مع أداة واحدة تكون في المنتصف، ومع اثنتين تكونان جنبًا إلى جنب. اختر حتى 3 كيانات لكل أداة من قائمة الكيانات الخاصة بك؛ اترك الاسم فارغًا لاستخدام اسم الكيان نفسه.", "होमपेज पर मौसम के ऊपर अपने Home Assistant सेंसर वाले 2 विजेट तक दिखाएं। एक विजेट के साथ यह केंद्र में होता है, दो के साथ वे साथ-साथ होते हैं। प्रत्येक विजेट के लिए अपनी एंटिटी सूची से 3 एंटिटी तक चुनें; एंटिटी के अपने नाम का उपयोग करने के लिए नाम खाली छोड़ें।", "Ana sayfada hava durumunun üzerinde kendi Home Assistant sensörlerinizle en fazla 2 widget gösterin. Tek widget'ta ortalanır, iki widget'ta yan yana durur. Widget başına Varlıklar listenizden en fazla 3 varlık seçin; varlığın kendi adını kullanmak için adı boş bırakın.", "Pokaż do 2 widżetów z własnymi czujnikami Home Assistant nad pogodą na stronie głównej. Przy jednym widżecie jest on wyśrodkowany, przy dwóch znajdują się obok siebie. Wybierz do 3 encji na widżet z listy Encji; pozostaw nazwę pustą, aby użyć nazwy samej encji.", "Tampilkan hingga 2 widget dengan sensor Home Assistant Anda sendiri di atas cuaca di halaman utama. Dengan satu widget, posisinya di tengah; dengan dua, berdampingan. Pilih hingga 3 entitas per widget dari daftar Entitas Anda; biarkan nama kosong untuk menggunakan nama entitas itu sendiri.", "Показуйте до 2 віджетів із власними датчиками Home Assistant над погодою на головній сторінці. За одного віджета він по центру, за двох — поруч. Виберіть для кожного віджета до 3 сутностей зі списку сутностей; залиште ім'я порожнім, щоб використати власну назву сутності.", "Hiển thị tối đa 2 tiện ích với cảm biến Home Assistant riêng phía trên thời tiết trên trang chủ. Với một tiện ích, nó ở giữa; với hai, chúng nằm cạnh nhau. Chọn tối đa 3 thực thể cho mỗi tiện ích từ danh sách Thực thể của bạn; để trống tên để dùng tên riêng của thực thể."
        ),
        "weather_widgets_no_entities" to t(
            "Nog geen entiteiten toegevoegd. Voeg ze eerst toe bij Home Assistant > Entiteiten.", "No entities added yet. Add them first under Home Assistant > Entities.", "Aún no se han añadido entidades. Añádelas primero en Home Assistant > Entidades.", "Ainda não foram adicionadas entidades. Adiciona-as primeiro em Home Assistant > Entidades.", "Noch keine Entitäten hinzugefügt. Füge sie zuerst unter Home Assistant > Entitäten hinzu.", "Aucune entité ajoutée pour le moment. Ajoutez-les d'abord dans Home Assistant > Entités.", "Nessuna entità ancora aggiunta. Aggiungile prima in Home Assistant > Entità.", "아직 엔티티가 추가되지 않았습니다. Home Assistant > 엔티티에서 먼저 추가하세요.", "尚未添加任何实体。请先在 Home Assistant > 实体中添加。", "まだエンティティが追加されていません。先にHome Assistant＞エンティティで追加してください。",
            "Сущности ещё не добавлены. Сначала добавьте их в Home Assistant > Сущности.", "لم يتم إضافة أي كيانات بعد. أضفها أولاً في Home Assistant > الكيانات.", "अभी तक कोई एंटिटी नहीं जोड़ी गई। पहले Home Assistant > एंटिटीज़ में जोड़ें।", "Henüz varlık eklenmedi. Önce Home Assistant > Varlıklar bölümünden ekleyin.", "Nie dodano jeszcze żadnych encji. Dodaj je najpierw w Home Assistant > Encje.", "Belum ada entitas ditambahkan. Tambahkan terlebih dahulu di Home Assistant > Entitas.", "Сутності ще не додано. Спочатку додайте їх у Home Assistant > Сутності.", "Chưa thêm thực thể nào. Hãy thêm trước trong Home Assistant > Thực thể."
        ),
        "weather_widget_1_header" to t(
            "WIDGET 1", "WIDGET 1", "WIDGET 1", "WIDGET 1", "WIDGET 1", "WIDGET 1", "WIDGET 1", "위젯 1", "小组件 1", "ウィジェット1",
            "ВИДЖЕТ 1", "الأداة 1", "विजेट 1", "WIDGET 1", "WIDŻET 1", "WIDGET 1", "ВІДЖЕТ 1", "TIỆN ÍCH 1"
        ),
        "weather_widget_2_header" to t(
            "WIDGET 2", "WIDGET 2", "WIDGET 2", "WIDGET 2", "WIDGET 2", "WIDGET 2", "WIDGET 2", "위젯 2", "小组件 2", "ウィジェット2",
            "ВИДЖЕТ 2", "الأداة 2", "विजेट 2", "WIDGET 2", "WIDŻET 2", "WIDGET 2", "ВІДЖЕТ 2", "TIỆN ÍCH 2"
        ),
        "weather_entity_id_label" to t(
            "Entiteit-id", "Entity ID", "ID de entidad", "ID da entidade", "Entitäts-ID", "ID d'entité", "ID entità", "엔티티 ID", "实体 ID", "エンティティID",
            "ID сущности", "معرف الكيان", "एंटिटी आईडी", "Varlık kimliği", "ID encji", "ID entitas", "ID сутності", "ID thực thể"
        ),
        "weather_choose_entity_placeholder" to t(
            "Kies een entiteit", "Choose an entity", "Elige una entidad", "Escolhe uma entidade", "Wähle eine Entität", "Choisissez une entité", "Scegli un'entità", "엔티티를 선택하세요", "选择一个实体", "エンティティを選択",
            "Выберите сущность", "اختر كيانًا", "एक एंटिटी चुनें", "Bir varlık seçin", "Wybierz encję", "Pilih entitas", "Виберіть сутність", "Chọn một thực thể"
        ),
        "weather_expand_content_desc" to t(
            "Uitklappen", "Expand", "Desplegar", "Expandir", "Ausklappen", "Développer", "Espandi", "펼치기", "展开", "展開",
            "Развернуть", "توسيع", "विस्तार करें", "Genişlet", "Rozwiń", "Perluas", "Розгорнути", "Mở rộng"
        ),
        "weather_no_entities_available" to t(
            "Geen entiteiten beschikbaar", "No entities available", "No hay entidades disponibles", "Nenhuma entidade disponível", "Keine Entitäten verfügbar", "Aucune entité disponible", "Nessuna entità disponibile", "사용 가능한 엔티티가 없습니다", "没有可用的实体", "利用可能なエンティティがありません",
            "Нет доступных сущностей", "لا توجد كيانات متاحة", "कोई एंटिटी उपलब्ध नहीं", "Kullanılabilir varlık yok", "Brak dostępnych encji", "Tidak ada entitas yang tersedia", "Немає доступних сутностей", "Không có thực thể khả dụng"
        ),
        "weather_custom_name_label" to t(
            "Eigen naam (optioneel)", "Custom name (optional)", "Nombre propio (opcional)", "Nome próprio (opcional)", "Eigener Name (optional)", "Nom personnalisé (facultatif)", "Nome personalizzato (opzionale)", "사용자 지정 이름(선택 사항)", "自定义名称（可选）", "任意の名前（任意）",
            "Собственное имя (необязательно)", "اسم مخصص (اختياري)", "अपना नाम (वैकल्पिक)", "Özel ad (isteğe bağlı)", "Własna nazwa (opcjonalnie)", "Nama khusus (opsional)", "Власна назва (необов'язково)", "Tên tùy chỉnh (không bắt buộc)"
        ),
        "weather_hour_short_label" to t(
            "uur", "hr", "h", "h", "Std", "h", "h", "시", "时", "時",
            "ч", "س", "घं", "sa", "godz", "jam", "год", "giờ"
        ),
        "weather_min_short_label" to t(
            "min", "min", "min", "min", "Min", "min", "min", "분", "分", "分",
            "мин", "دق", "मि", "dk", "min", "mnt", "хв", "phút"
        ),
        "weather_confirm_title" to t(
            "Weet je het zeker?", "Are you sure?", "¿Estás seguro?", "Tens a certeza?", "Bist du sicher?", "Êtes-vous sûr ?", "Sei sicuro?", "확실합니까?", "确定吗？", "よろしいですか？",
            "Вы уверены?", "هل أنت متأكد؟", "क्या आप निश्चित हैं?", "Emin misiniz?", "Czy na pewno?", "Apakah Anda yakin?", "Ви впевнені?", "Bạn có chắc không?"
        ),
        "weather_confirm_copy_calendars_desc" to t(
            "Dit vervangt je huidige agenda-selectie hier door dezelfde agenda's als bij '{other}'.", "This will replace your current calendar selection here with the same calendars as '{other}'.", "Esto reemplazará tu selección de calendarios actual aquí por los mismos calendarios que en '{other}'.", "Isto substitui a tua seleção de agendas atual aqui pelas mesmas agendas de '{other}'.", "Dies ersetzt deine aktuelle Kalenderauswahl hier durch dieselben Kalender wie bei '{other}'.", "Cela remplacera votre sélection actuelle de calendriers ici par les mêmes calendriers que pour '{other}'.", "Questo sostituirà la tua selezione attuale di calendari qui con gli stessi calendari di '{other}'.", "이렇게 하면 여기의 현재 일정 선택이 '{other}'와 동일한 일정으로 대체됩니다.", "这将把此处当前的日历选择替换为与\"{other}\"相同的日历。", "これにより、ここでの現在のカレンダー選択が「{other}」と同じカレンダーに置き換わります。",
            "Это заменит текущий выбор календарей здесь на те же календари, что и в '{other}'.", "سيؤدي هذا إلى استبدال اختيار التقويم الحالي هنا بنفس التقويمات الموجودة في '{other}'.", "यह यहां आपके वर्तमान कैलेंडर चयन को '{other}' के समान कैलेंडर से बदल देगा।", "Bu, buradaki mevcut takvim seçiminizi '{other}' ile aynı takvimlerle değiştirecektir.", "To zastąpi Twój bieżący wybór kalendarzy tutaj tymi samymi kalendarzami co w '{other}'.", "Ini akan mengganti pilihan kalender Anda saat ini di sini dengan kalender yang sama seperti di '{other}'.", "Це замінить поточний вибір календарів тут на ті самі календарі, що й у '{other}'.", "Điều này sẽ thay thế lựa chọn lịch hiện tại của bạn ở đây bằng cùng các lịch như ở '{other}'."
        ),
        "weather_confirm_use_same" to t(
            "Ja, gebruik dezelfde", "Yes, use the same", "Sí, usar las mismas", "Sim, usar as mesmas", "Ja, dieselben verwenden", "Oui, utiliser les mêmes", "Sì, usa gli stessi", "예, 동일하게 사용", "是的，使用相同", "はい、同じものを使う",
            "Да, использовать те же", "نعم، استخدم نفسها", "हां, समान का उपयोग करें", "Evet, aynısını kullan", "Tak, użyj tych samych", "Ya, gunakan yang sama", "Так, використати ті самі", "Có, dùng giống nhau"
        ),
        "weather_use_same_calendars_button" to t(
            "Zelfde agenda's als '{other}' gebruiken", "Use the same calendars as '{other}'", "Usar los mismos calendarios que '{other}'", "Usar as mesmas agendas de '{other}'", "Dieselben Kalender wie bei '{other}' verwenden", "Utiliser les mêmes calendriers que '{other}'", "Usa gli stessi calendari di '{other}'", "'{other}'와 동일한 일정 사용", "使用与\"{other}\"相同的日历", "「{other}」と同じカレンダーを使う",
            "Использовать те же календари, что и '{other}'", "استخدام نفس التقويمات كما في '{other}'", "'{other}' के समान कैलेंडर का उपयोग करें", "'{other}' ile aynı takvimleri kullan", "Użyj tych samych kalendarzy co '{other}'", "Gunakan kalender yang sama seperti '{other}'", "Використати ті самі календарі, що й '{other}'", "Dùng cùng lịch như '{other}'"
        ),
        "weather_calendar_selection_default_off_desc" to t(
            "Standaard staat alles uit. Niets aangevinkt = geen agenda's tellen mee — selecteer zelf welke agenda's meetellen.", "Everything is off by default. Nothing checked = no calendars count — select yourself which calendars count.", "Por defecto todo está desactivado. Nada marcado = ningún calendario cuenta — selecciona tú mismo qué calendarios cuentan.", "Por predefinição, tudo está desativado. Nada assinalado = nenhuma agenda conta — seleciona tu quais agendas contam.", "Standardmäßig ist alles aus. Nichts angehakt = keine Kalender zählen — wähle selbst, welche Kalender zählen.", "Tout est désactivé par défaut. Rien de coché = aucun calendrier ne compte — sélectionnez vous-même les calendriers qui comptent.", "Per impostazione predefinita, tutto è disattivato. Nulla selezionato = nessun calendario conta — scegli tu quali calendari contano.", "기본적으로 모두 꺼져 있습니다. 아무것도 선택하지 않으면 어떤 일정도 반영되지 않습니다 — 반영할 일정을 직접 선택하세요.", "默认全部关闭。未勾选任何项 = 没有日历计入——请自行选择哪些日历计入。", "デフォルトではすべてオフです。何もチェックしない＝どのカレンダーも反映されません。反映するカレンダーを自分で選択してください。",
            "По умолчанию всё выключено. Ничего не отмечено = ни один календарь не учитывается — выберите сами, какие календари учитывать.", "كل شيء متوقف افتراضيًا. لا شيء محدد = لا يتم احتساب أي تقويم — اختر بنفسك أي التقويمات يتم احتسابها.", "डिफ़ॉल्ट रूप से सब कुछ बंद है। कुछ भी चेक नहीं = कोई कैलेंडर नहीं गिना जाता — स्वयं चुनें कि कौन से कैलेंडर गिने जाएं।", "Varsayılan olarak her şey kapalıdır. Hiçbir şey işaretlenmemiş = hiçbir takvim sayılmaz — hangi takvimlerin sayılacağını kendiniz seçin.", "Domyślnie wszystko jest wyłączone. Nic niezaznaczone = żaden kalendarz się nie liczy — wybierz samodzielnie, które kalendarze mają się liczyć.", "Secara default semuanya mati. Tidak ada yang dicentang = tidak ada kalender yang dihitung — pilih sendiri kalender mana yang dihitung.", "За замовчуванням усе вимкнено. Нічого не відмічено = жоден календар не враховується — виберіть самостійно, які календарі враховувати.", "Mặc định tất cả đều tắt. Không chọn gì = không có lịch nào được tính — hãy tự chọn lịch nào được tính."
        ),
        "weather_no_calendars_found" to t(
            "Geen agenda's gevonden", "No calendars found", "No se encontraron calendarios", "Nenhuma agenda encontrada", "Keine Kalender gefunden", "Aucun calendrier trouvé", "Nessun calendario trovato", "일정을 찾을 수 없습니다", "未找到日历", "カレンダーが見つかりません",
            "Календари не найдены", "لم يتم العثور على تقويمات", "कोई कैलेंडर नहीं मिला", "Takvim bulunamadı", "Nie znaleziono kalendarzy", "Tidak ditemukan kalender", "Календарів не знайдено", "Không tìm thấy lịch nào"
        ),
        "weather_fetch_forecast_error" to t(
            "Kon de weersverwachting niet ophalen", "Could not fetch the weather forecast", "No se pudo obtener el pronóstico del tiempo", "Não foi possível obter a previsão do tempo", "Wettervorhersage konnte nicht abgerufen werden", "Impossible de récupérer les prévisions météo", "Impossibile recuperare le previsioni meteo", "일기 예보를 가져올 수 없습니다", "无法获取天气预报", "天気予報を取得できませんでした",
            "Не удалось получить прогноз погоды", "تعذر جلب توقعات الطقس", "मौसम पूर्वानुमान प्राप्त नहीं किया जा सका", "Hava durumu tahmini alınamadı", "Nie udało się pobrać prognozy pogody", "Tidak dapat mengambil prakiraan cuaca", "Не вдалося отримати прогноз погоди", "Không thể lấy dự báo thời tiết"
        )
    )

    fun getString(key: String): String {
        val langCode = currentLanguage.value.code
        return translations[key]?.get(langCode) ?: translations[key]?.get("nl") ?: key
    }
}
