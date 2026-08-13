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
package com.infomaniak.mail.data.models

data class MailTemplate(
    val id: Int,
    val title: String,
    val body: String,
) {
    companion object {
        val mocks: List<MailTemplate> = listOf(
            MailTemplate(
                id = 1,
                title = "Bienvenue chez Infomaniak",
                body = """
                <h1>Bienvenue ! 👋</h1>
                <p>Nous sommes ravis de vous compter parmi nous. Votre compte est désormais actif et vous pouvez accéder à tous nos services.</p>
                <p><strong>Prochaines étapes :</strong></p>
                <ul>
                    <li>Configurez votre nom de domaine</li>
                    <li>Découvrez kDrive pour stocker vos fichiers</li>
                    <li>Personnalisez votre adresse e-mail</li>
                </ul>
                <p>Notre équipe support est disponible 24/7 pour vous accompagner.</p>
                <p style="color: #666; font-size: 12px;">Cordialement, L'équipe Infomaniak</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 2,
                title = "Confirmation de commande",
                body = """
                <h2>Merci pour votre commande</h2>
                <p>Nous avons bien reçu votre commande <strong>#CMD-2026-8472</strong>.</p>
                <table style="width: 100%; border-collapse: collapse;">
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">kDrive Pro 1 To</td><td style="padding: 8px; border: 1px solid #ddd;">CHF 10.50/mois</td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Nom de domaine .ch</td><td style="padding: 8px; border: 1px solid #ddd;">CHF 15.00/an</td></tr>
                </table>
                <p>Vous recevrez un e-mail séparé lorsque vos services seront activés.</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 3,
                title = "Relance facture impayée",
                body = """
                <p><strong>Objet : Facture #FAC-2026-00342 en attente de paiement</strong></p>
                <p>Bonjour,</p>
                <p>Sauf erreur de notre part, nous n'avons pas encore reçu le règlement de votre facture d'un montant de <strong>CHF 47.90</strong>, arrivée à échéance le <em>15 juillet 2026</em>.</p>
                <div style="background: #fff3cd; padding: 12px; border-left: 4px solid #ffc107; margin: 16px 0;">
                    ⚠️ <strong>Rappel :</strong> Vos services pourraient être suspendus sous 7 jours sans régularisation.
                </div>
                <p>Vous pouvez régler en ligne depuis votre <a href="#">espace client</a>.</p>
                <p>En cas de paiement récent, merci de ne pas tenir compte de ce message.</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 4,
                title = "Invitation réunion projet",
                body = """
                <h2>Réunion de lancement — Projet Migration Cloud</h2>
                <p>Bonjour à toutes et à tous,</p>
                <p>Vous êtes convié(e) à la réunion de lancement qui se tiendra le :</p>
                <blockquote>
                    📅 <strong>Date :</strong> Mardi 18 août 2026<br>
                    🕐 <strong>Heure :</strong> 14h00 - 15h30<br>
                    📍 <strong>Lieu :</strong> kMeet (lien envoyé séparément)
                </blockquote>
                <p><strong>Ordre du jour :</strong></p>
                <ol>
                    <li>Présentation des objectifs et périmètre</li>
                    <li>Architecture technique proposée</li>
                    <li>Planning et jalons clés</li>
                    <li>Questions diverses</li>
                </ol>
                <p>Merci de confirmer votre présence avant le 14 août.</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 5,
                title = "Réinitialisation du mot de passe",
                body = """
                <p>Vous avez demandé à réinitialiser votre mot de passe.</p>
                <p>Cliquez sur le bouton ci-dessous pour définir un nouveau mot de passe :</p>
                <div style="text-align: center; margin: 24px 0;">
                    <a href="#" style="background: #0066cc; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">Réinitialiser mon mot de passe</a>
                </div>
                <p style="color: #999; font-size: 11px;">Ce lien expire dans 1 heure. Si vous n'avez pas demandé cette réinitialisation, ignorez cet e-mail.</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 6,
                title = "",
                body = """
                <p>Cher client,</p>
                <p>Nous vous informons d'une maintenance planifiée de nos serveurs le <strong>dimanche 23 août 2026 de 2h00 à 4h00</strong>.</p>
                <p>Pendant cette fenêtre, les services suivants pourraient être temporairement indisponibles :</p>
                <ul>
                    <li>kDrive</li>
                    <li>kMail</li>
                    <li>API Public Cloud</li>
                </ul>
                <p>Nous nous excusons pour la gêne occasionnée.</p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 7,
                title = "Rapport mensuel d'utilisation",
                body = """
                <h2>📊 Votre consommation de juillet 2026</h2>
                <p>Voici le récapitulatif de votre utilisation :</p>
                <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
                    <tr style="background: #f5f5f5;">
                        <th style="padding: 8px; text-align: left;">Service</th>
                        <th style="padding: 8px;">Utilisé</th>
                        <th style="padding: 8px;">Limite</th>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #ddd;">kDrive Stockage</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">742 Go</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">1 To</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #ddd;">kMail Envoi</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">1 247 emails</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">Illimité</td>
                    </tr>
                </table>
                <p><a href="#">Accéder à mon espace client →</a></p>
                """.trimIndent(),
            ),
            MailTemplate(
                id = 8,
                title = "Pas de body",
                body = "",
            ),
            MailTemplate(
                id = 9,
                title = "Vrai mail complet",
                body = """
                <div>
                 <br>
                </div>
                <div>
                 C'est un test : <b>gras</b>
                </div>
                <div>
                 <b><br></b>
                </div>
                <div>
                Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.
                </div>
                <div>
                 <i>&nbsp;Italic. &nbsp;<b>Gras italique&nbsp;</b></i>
                </div>
                <div>
                 <u>Surligneur&nbsp;</u>
                </div>
                <div>
                 <strike>
                  Barre&nbsp;
                 </strike>
                </div>
                <div>
                 <ul>
                  <li>Liste</li>
                  <li>Liste2</li>
                 </ul>
                 <div>
                  <a href="https://google.com">Lien</a>
                  <br>
                 </div>
                </div>
                """.trimIndent(),
            ),
        )
    }
}
