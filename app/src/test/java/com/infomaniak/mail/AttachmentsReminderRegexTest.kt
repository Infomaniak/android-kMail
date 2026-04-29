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

package com.infomaniak.mail

import com.infomaniak.mail.utils.AttachmentsReminderUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsReminderRegexTest {

    /* Empty string test */
    @Test
    fun empty_and_blank_strings_isIgnored() {
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword(""))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("   "))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("\n\t"))
    }

    @Test
    fun sentences_without_keywords_isIgnored() {
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Bonjour, comment allez-vous ?"))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("I will send the email tomorrow."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Können Sie mir das schicken?"))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Non ho capito la tua richiesta."))
    }

    /* Edge case and False Positives test */

    @Test
    fun case_insensitivity_isIgnored() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("PLEASE FIND ATTACHED THE FILE."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("vOiR lA PièCe JoInTe"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("SIEHE ANHANG"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("ZOBACZ ZAŁĄCZNIK"))
    }

    @Test
    fun words_containing_keyword_prevent_false_positives_isIgnored() {
        // English false positives
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("The device is unattached from the port."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("He remains emotionally unattached."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("They adjoined the two rooms."))

        // French false positives
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Ce sont des zones disjointes."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Il faut que je te rejoigne plus tard."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Nous travaillons conjointement sur ce projet."))
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("La menuiserie a des jointures parfaites."))

        // Spanish false positives
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("El coadjunto no vino a trabajar hoy."))

        // Italian false positives
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Sono rallegrato dalla notizia."))

        // German false positives
        assertFalse(AttachmentsReminderUtils.hasAttachmentKeyword("Wir sind unangehängt."))
    }

    @Test
    fun multi_line_strings_isDetected() {
        val multiLineText = """
            Bonjour,
            
            Suite à notre appel, veuillez trouver 
            en annexe le récapitulatif.
            
            Cordialement,
        """.trimIndent()
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword(multiLineText))
    }

    @Test
    fun accents_and_special_chars_in_french_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Voir la pièce jointe"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Voir la piece jointe"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Voir la piéce jointe"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("cf pj"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("cf. pj"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("cf. p.j."))
    }

    /* Positive test for different languages */

    @Test
    fun french_positive_cases_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Veuillez trouver ci-joint le document."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Je te joins le fichier de ce matin."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Il a joint les photos."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Regarde les fichiers associés à cet e-mail."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Les documents en annexe sont prêts."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Voici la facture jointe à ce message."))
    }

    @Test
    fun english_positive_cases_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Please see the attached file."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("I've attached the invoice."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Attached is the document you requested."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Here is the attachment you wanted."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("You will find the attached files below."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Attached to this email are the logs."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("See included reports."))
    }

    @Test
    fun german_positive_cases_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("siehe Anhang für weitere Details."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Die Datei ist angehängt."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Im Anhang finden Sie die Rechnung."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Ich habe einen Anhang hinzugefügt."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("In der Anlage sende ich Ihnen das Formular."))
    }

    @Test
    fun spanish_positive_cases_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Te envío el archivo adjunto."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("He adjuntado un archivo para ti."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Ver el archivo incluido."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Se han adjuntado los informes."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Adjunto a este correo encontrarás las fotos."))
    }

    @Test
    fun italian_positive_cases_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Vedi in allegato il documento."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("In allegato trovi il file."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Ti allego la fattura."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Ho allegato il contratto firmato."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Vedi l'allegato per le istruzioni."))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("Incluso troverai il riepilogo."))
    }

    @Test
    fun other_languages_positive_cases_isDetected() {
        // Danish
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("se vedhæftet fil"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("jeg vedhæfter dokumenterne"))
        // Dutch
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("zie bijlage voor meer info"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("bijgevoegde bestanden zijn groot"))
        // Finnish
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("katso liite tässä"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("löytyy liitteenä sähköpostissa"))
        // Greek
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("δείτε συνημμένο το αρχείο"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("επισυνάπτω την αναφορά"))
        // Norwegian
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("se vedlegg for detaljer"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("jeg sender vedlagt fakturaen"))
        // Polish
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("zobacz załącznik w wiadomości"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("załączone pliki do sprawdzenia"))
        // Portuguese
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("segue anexo o contrato"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("arquivos anexos à mensagem"))
        // Swedish
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("se bifogad fil nedan"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("jag skickar bifogat kvitto"))
    }

    /* Advanced Edge Cases & Potential Regex Failures */

    @Test
    fun apostrophes_and_contractions_isDetected() {
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("C'est la pièce-jointe"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("I've attached it"))
        assertTrue(AttachmentsReminderUtils.hasAttachmentKeyword("J'ai joint le fichier"))
    }
}
