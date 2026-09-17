package com.example.ai.assistant

class FallbackAssistantPlanner : AssistantPlanner {

    override suspend fun createPlan(
        prompt: String,
        context: AssistantContext
    ): AssistantResult {
        val normalized = normalize(prompt)

        if (normalized.isBlank()) {
            return AssistantResult.Unsupported(
                prompt = prompt,
                reason = "Empty instruction"
            )
        }

        if (!context.hasSourceMedia && !isUndoRedo(normalized)) {
            return AssistantResult.Unsupported(
                prompt = prompt,
                reason = "No source media is currently open."
            )
        }

        val actions = mutableListOf<AssistantAction>()

        if (containsAny(normalized, "undo", "تراجع", "رجوع خطوة", "الغاء اخر")) {
            actions += AssistantAction.Undo
        }

        if (containsAny(normalized, "redo", "إعادة", "اعادة", "إعادة الخطوة")) {
            actions += AssistantAction.Redo
        }

        if (containsAny(
                normalized,
                "background",
                "remove bg",
                "remove the bg",
                "remove background",
                "شيل الخلفية",
                "ازالة الخلفية",
                "إزالة الخلفية",
                "احذف الخلفية",
                "حذف الخلفية"
            )
        ) {
            actions += AssistantAction.RemoveBackground
        }

        val darkerRequest = containsAny(
            normalized,
            "darker",
            "darken",
            "less bright",
            "less light",
            "make it dark",
            "make it darker",
            "أغمق",
            "غمق",
            "غمقها",
            "اقل إضاءة",
            "أقل إضاءة"
        )

        if (darkerRequest || containsAny(
                normalized,
                "bright",
                "brighter",
                "brightness",
                "make it light",
                "more light",
                "زود الاضاءة",
                "زود الإضاءة",
                "إضاءة أكثر",
                "خليها افتح",
                "خلي الصورة افتح",
                "أفتح",
                "افتح الصورة",
                "افتحها"
            )
        ) {
            actions += AssistantAction.AdjustBrightness(if (darkerRequest) -25f else 25f)
        }

        if (containsAny(normalized, "contrast", "تباين", "التباين")) {
            val amount = if (containsAny(
                    normalized,
                    "decrease contrast",
                    "reduce contrast",
                    "less contrast",
                    "قلل التباين",
                    "خفض التباين"
                )) -0.10f else 0.10f
            actions += AssistantAction.AdjustContrast(amount)
        }

        if (containsAny(
                normalized,
                "saturation",
                "saturated",
                "more colorful",
                "more colour",
                "colors stronger",
                "تشبع",
                "ألوان أكثر",
                "الوان اكثر",
                "خلي الالوان اقوى",
                "خلي الألوان أقوى"
            )
        ) {
            val amount = if (containsAny(
                    normalized,
                    "less saturation",
                    "desaturate",
                    "less colorful",
                    "أقل تشبع",
                    "قلل التشبع",
                    "ألوان أقل"
                )) -0.10f else 0.10f
            actions += AssistantAction.AdjustSaturation(amount)
        }

        if (containsAny(
                normalized,
                "warmer",
                "warm",
                "temperature warm",
                "دفء",
                "دافئ",
                "دافيه",
                "دافية",
                "أدفى",
                "ادفى",
                "خليها دافية",
                "خلي الصورة دافئة"
            )
        ) {
            actions += AssistantAction.AdjustTemperature(0.10f)
        }

        if (containsAny(
                normalized,
                "cooler",
                "cool",
                "colder",
                "أبرد",
                "ابرد",
                "أبرد قليلا",
                "برد الألوان",
                "برد الالوان",
                "أقل دفء",
                "اقل دفء"
            )
        ) {
            actions += AssistantAction.AdjustTemperature(-0.10f)
        }

        if (containsAny(
                normalized,
                "enhance",
                "improve quality",
                "improve image",
                "sharpen",
                "enhance image",
                "تحسين الجودة",
                "حسن الجودة",
                "حسّن الجودة",
                "حسن الصورة",
                "وضّح الصورة",
                "وضح الصورة",
                "زيادة الحدة",
                "حدة أكثر"
            )
        ) {
            actions += AssistantAction.EnhanceImage
        }

        if (containsAny(
                normalized,
                "upscale",
                "higher resolution",
                "increase resolution",
                "2x",
                "رفع الدقة",
                "دقة أعلى",
                "كبر الصورة",
                "كبرها",
                "تكبير الصورة"
            )
        ) {
            actions += AssistantAction.UpscaleImage
        }

        if (actions.isEmpty()) {
            return AssistantResult.Unsupported(
                prompt = prompt,
                reason = "The instruction could not be mapped to a supported editing action."
            )
        }

        val explanation = actions.joinToString(separator = "\n", transform = ::formatAction)

        val requiresConfirmation = actions.any {
            it is AssistantAction.RemoveBackground ||
                it is AssistantAction.EnhanceImage ||
                it is AssistantAction.UpscaleImage
        }

        return AssistantResult.Planned(
            AssistantActionPlan(
                originalPrompt = prompt,
                actions = actions,
                explanation = explanation,
                requiresConfirmation = requiresConfirmation
            )
        )
    }

    private fun normalize(prompt: String): String {
        return prompt
            .trim()
            .lowercase()
            .replace('\u0640'.toString(), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
    }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any { text.contains(normalize(it)) }

    private fun isUndoRedo(text: String): Boolean =
        containsAny(text, "undo", "redo", "تراجع", "رجوع", "اعادة", "إعادة")

    private fun formatAction(action: AssistantAction): String {
        return when (action) {
            is AssistantAction.AdjustBrightness ->
                "Adjust brightness by ${action.amount}"
            is AssistantAction.AdjustContrast ->
                "Adjust contrast by ${action.amount}"
            is AssistantAction.AdjustSaturation ->
                "Adjust saturation by ${action.amount}"
            is AssistantAction.AdjustTemperature ->
                "Adjust temperature by ${action.amount}"
            is AssistantAction.ApplyFilter ->
                "Apply filter ${action.filterId}"
            AssistantAction.RemoveBackground ->
                "Remove background"
            AssistantAction.EnhanceImage ->
                "Enhance image"
            AssistantAction.UpscaleImage ->
                "Upscale image"
            AssistantAction.Undo ->
                "Undo"
            AssistantAction.Redo ->
                "Redo"
            AssistantAction.ExportMedia ->
                "Export media"
            is AssistantAction.Unknown ->
                "Unsupported instruction"
        }
    }
}
