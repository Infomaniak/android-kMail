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

object AttachmentsReminderUtils {
    private val frAttachmentsReminderRegex = listOf(
        "voir pi[eèé]ces? jointes?",
        "fichiers? joints?",
        "fichiers? associ[eèé]s?",
        "jointe?s? [aà] cet e-mail",
        "jointe?s? [aà] ce message",
        "je ((te|vous) )?joins",
        "j'ai joint",
        "ci(-| )joint",
        "cf\\.?\\s*(?:p\\.?j\\.?|pi[eèé]ces? jointes?)",
        "pi[eèé]ces?(-|\\s)jointes?",
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

    private val enAttachmentsReminderRegex = listOf(
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

    private val deAttachmentsReminderRegex = listOf(
        "siehe Anhang",
        "\\bangeh\u00e4ngt\\b",
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

    private val esAttachmentsReminderRegex = listOf(
        "el archivo (adjunto|incluido)",
        "los archivos (adjuntos|incluidos)",
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
        "[^\\w]adjunto?s?[^\\w]"
    ).joinToString("|")

    private val itAttachmentsReminderRegex = listOf(
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


    private val daAttachmentsReminderRegex = listOf(
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

    private val nlAttachmentsReminderRegex = listOf(
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

    private val fiAttachmentsReminderRegex = listOf(
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

    private val elAttachmentsReminderRegex = listOf(
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

    private val nbAttachmentsReminderRegex = listOf(
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

    private val plAttachmentsReminderRegex = listOf(
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

    private val ptAttachmentsReminderRegex = listOf(
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

    private val svAttachmentsReminderRegex = listOf(
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
        frAttachmentsReminderRegex,
        enAttachmentsReminderRegex,
        deAttachmentsReminderRegex,
        esAttachmentsReminderRegex,
        itAttachmentsReminderRegex,
        daAttachmentsReminderRegex,
        nlAttachmentsReminderRegex,
        fiAttachmentsReminderRegex,
        elAttachmentsReminderRegex,
        nbAttachmentsReminderRegex,
        plAttachmentsReminderRegex,
        ptAttachmentsReminderRegex,
        svAttachmentsReminderRegex,
    ).joinToString("|")

    private val pattern = Regex(fullRegex, RegexOption.IGNORE_CASE)

    fun hasAttachmentKeyword(text: String): Boolean = pattern.containsMatchIn(text)
}

