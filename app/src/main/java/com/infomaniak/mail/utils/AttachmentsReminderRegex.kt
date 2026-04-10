/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.utils

object AttachmentReminderUtils {

    private val FR_ATTACHMENTS_REMINDER_REGEX = listOf(
        "voir pi[eèé]ces? jointes?",
        "voir fichiers? joints?",
        "voir fichiers? associ[eèé]s?",
        "jointe?s? [aà] cet e-mail",
        "jointe?s? [aà] ce message",
        "je ((te|vous) )?joins",
        "j'ai joint",
        "ci(-| )joint",
        "cf\\.?\\s*(pj|pi[eèé]ces? jointes?)",
        "pi[eèé]ces? jointes?",
        "fichiers? joints?",
        "voir le fichier joint",
        "voir les fichiers joints",
        "voir la pi[eèé]ce jointe",
        "voir les pi[eèé]ces jointes",
        "en annexe",
        "trouvez ci-joint",
        "veuillez trouver ci-joint",
        "[^\\w]jointe?s?[^\\w]"
    ).joinToString("|")

    private val EN_ATTACHMENTS_REMINDER_REGEX = listOf(
        "see (the\\s)?attach(ed|ments?)",
        "see included",
        "is attached",
        "attached is",
        "are attached",
        "attached are",
        "attached to this email",
        "attached to this message",
        "I('|\\sa)m attaching",
        "I('|\\sha)ve attached",
        "I attache?d?",
        "find (the\\s)?(attached|included)",
        "attached files?",
        "here is the attachment",
        "please find attached",
        "attached you will find",
        "[^\\w]attached[^\\w]"
    ).joinToString("|")

    private val DE_ATTACHMENTS_REMINDER_REGEX = listOf(
        "siehe Anhang",
        "angeh\u00e4ngt",
        "hinzugef\u00fcgt",
        "Anhang hinzuf\u00fcgen",
        "Anhang anbei",
        "Anhang hinzugef\u00fcgt",
        "anbei finden",
        "anbei",
        "im Anhang",
        "mit dieser E-Mail sende ich",
        "angeh\u00e4ngte Datei",
        "siehe angeh\u00e4ngte Datei",
        "siehe Anh\u00e4nge",
        "angeh\u00e4ngte Dateien",
        "in der Anlage",
        "anbei sende ich",
        "[^\\w]siehe Anlagen?[^\\w]"
    ).joinToString("|")

    private val ES_ATTACHMENTS_REMINDER_REGEX = listOf(
        "ver (el\\s)?(archivo\\s)?(adjunto|incluido)",
        "se ha adjuntado",
        "adjuntados?",
        "se ha adjuntado a este (correo|mensaje)",
        "se han adjuntado",
        "Adjunto te env\u00edo",
        "He adjuntado",
        "He adjuntado un archivo",
        "adjunto el archivo",
        "incluyo el archivo",
        "archivo adjunto",
        "ver archivos adjuntos",
        "archivos adjuntos",
        "a\u00f1adido adjunto",
        "adjunto a este correo",
        "en el adjunto",
        "[^\\w]adjunto[^\\w]"
    ).joinToString("|")

    private val IT_ATTACHMENTS_REMINDER_REGEX = listOf(
        "(vedi\\s)?(in\\s)?allegat(o|i)",
        "vedi accluso",
        "\u00e8 allegato",
        "sono allegati",
        "giunto a questo mail",
        "invio in allegato",
        "allego",
        "ho allegato",
        "in allegato trovi",
        "trova in allegato",
        "trova accluso",
        "incluso troverai",
        "file allegat(o|i)",
        "vedi l'allegato",
        "in allegato la/e",
        "allegato a quest(a|o) (mail|messaggio)",
        "in allegato (trova|trovi|la|le)",
        "[^\\w]ti allego[^\\w]"
    ).joinToString("|")


    private val DA_ATTACHMENTS_REMINDER_REGEX = listOf(
        "se vedhæftet",
        "vedhæftet fil",
        "vedhæftede filer",
        "jeg vedhæfter",
        "har vedhæftet",
        "findes vedhæftet",
        "er vedhæftet",
        "vedhæftet denne e-mail",
        "i vedhæftningen",
        "bilag",
        "se bilag",
        "vedhæftede? filer?",
        "[^\\w]vedhæftet[^\\w]"
    ).joinToString("|")

    private val NL_ATTACHMENTS_REMINDER_REGEX = listOf(
        "zie bijlage",
        "bijgevoegd",
        "in de bijlage",
        "ik voeg toe",
        "heb bijgevoegd",
        "bijgevoegde bestand",
        "bijgevoegde bestanden",
        "als bijlage",
        "zie bijgevoegd",
        "hierbij zend ik",
        "bij deze e-mail",
        "attenderen op bijlage",
        "bijgevoegd(e)?",
        "bijgevoegde? bestand(en)?",
        "[^\\w]bijlage[^\\w]"
    ).joinToString("|")

    private val FI_ATTACHMENTS_REMINDER_REGEX = listOf(
        "katso liite",
        "liitteenä",
        "liitetty",
        "olen liittänyt",
        "tässä liitteenä",
        "liitetiedosto",
        "liitetiedostot",
        "sähköpostin liite",
        "löytyy liitteenä",
        "lahetan liitteenä",
        "katso attached",
        "liitetiedosto(t)?",
        "ohessa",
        "[^\\w]liite[^\\w]"
    ).joinToString("|")

    private val EL_ATTACHMENTS_REMINDER_REGEX = listOf(
        "δείτε συνημμένο",
        "συνημμένα αρχεία",
        "επισυνάπτεται",
        "έχω επισυνάψει",
        "βλέπε συνημμένο",
        "στο συνημμένο",
        "συνημμένο σε αυτό το email",
        "θα βρείτε στο συνημμένο",
        "επισυνάπτω",
        "το αρχείο επισυνάπτεται",
        "[^\\w]συνημμέν[^\\w]"
    ).joinToString("|")

    private val NB_ATTACHMENTS_REMINDER_REGEX = listOf(
        "se vedlegg",
        "vedlagt fil",
        "vedlagte filer",
        "jeg vedlegger",
        "har vedlagt",
        "finnes vedlagt",
        "er vedlagt",
        "vedlagt denne e-posten",
        "i vedlegget",
        "vedlegg",
        "jeg sender vedlagt",
        "se vedlagte",
        "[^\\w]vedlagt[^\\w]"
    ).joinToString("|")

    private val PL_ATTACHMENTS_REMINDER_REGEX = listOf(
        "zobacz załącznik",
        "załączone pliki",
        "załącznik do tego maila",
        "załączam",
        "załączyłem",
        "załączyłam",
        "w załączniku",
        "plik załączony",
        "proszę zobaczyć załącznik",
        "znajdziesz w załączniku",
        "dołączono",
        "w załączeniu",
        "załączon(e|y) plik(i)?",
        "załącznik(i)? do tego maila",
        "załączył(em|am)",
        "[^\\w]załącznik[^\\w]"
    ).joinToString("|")

    private val PT_ATTACHMENTS_REMINDER_REGEX = listOf(
        "ver anexo",
        "ver anexos",
        "segue anexo",
        "seguem anexos",
        "em anexo",
        "anexado a este e-mail",
        "eu anexo",
        "anexei",
        "encontre em anexo",
        "arquivo anexo",
        "arquivos anexos",
        "documento anexo",
        "veja o anexo",
        "[^\\w]anexo[^\\w]"
    ).joinToString("|")

    private val SV_ATTACHMENTS_REMINDER_REGEX = listOf(
        "se bifogad fil",
        "bifogade filer",
        "jag bifogar",
        "har bifogat",
        "finns bifogat",
        "är bifogat",
        "bifogat till detta mejl",
        "i bilagan",
        "bilaga",
        "se bilaga",
        "jag skickar bifogat",
        "vidhäftad",
        "se bifogad(e)? fil(er)?",
        "[^\\w]bifogad[^\\w]"
    ).joinToString("|")

    private val fullRegex = listOf(
        FR_ATTACHMENTS_REMINDER_REGEX,
        EN_ATTACHMENTS_REMINDER_REGEX,
        DE_ATTACHMENTS_REMINDER_REGEX,
        ES_ATTACHMENTS_REMINDER_REGEX,
        IT_ATTACHMENTS_REMINDER_REGEX,
        DA_ATTACHMENTS_REMINDER_REGEX,
        NL_ATTACHMENTS_REMINDER_REGEX,
        FI_ATTACHMENTS_REMINDER_REGEX,
        EL_ATTACHMENTS_REMINDER_REGEX,
        NB_ATTACHMENTS_REMINDER_REGEX,
        PL_ATTACHMENTS_REMINDER_REGEX,
        PT_ATTACHMENTS_REMINDER_REGEX,
        SV_ATTACHMENTS_REMINDER_REGEX,
    ).joinToString("|")
    private val pattern = Regex(fullRegex, RegexOption.IGNORE_CASE)
    fun hasAttachmentKeyword(text: String): Boolean {
        return pattern.containsMatchIn(text)
    }
}

