/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

 function removeAllProperties() {
    const properties = [
        { name: 'position', values: ['absolute'] },
        // { name: '-webkit-text-size-adjust', values: [] } // For iOS only, because they're based on webkit
        // { name: 'height', values: ['100%'] }
    ];
    removeCSSProperty(properties)
}

function removeCSSProperty(properties) {
    removeFromInlineStyle(properties);
    removeFromStylesheets(properties);
}

function removeFromInlineStyle(properties) {
    for (const property of properties) {
        const elementsWithInlineStyle = document.querySelectorAll(`[style*=${property.name}]`);
        for (const element of elementsWithInlineStyle) {
            const propertyValue = element.style[property.name];
            if (shouldRemovePropertyForGivenValue(property, propertyValue)) {
                element.style[property.name] = null;
                console.info(`[FIX_EMAIL_STYLE] Remove property ${property.name} from inline style.`);
            }
        }
    }
}

function removeFromStylesheets(properties) {
    for (let i = 0; i < document.styleSheets.length; i++) {
        const styleSheet = document.styleSheets[i];
        try {
            removePropertiesForAllCSSRules(properties, styleSheet);
        } catch (error) {
            // The stylesheet cannot be modified
        }
    }
}

function removePropertiesForAllCSSRules(properties, styleSheet) {
    for (let j = 0; j < styleSheet.cssRules.length; j++) {
        for (const property of properties) {
            if (!styleSheet.cssRules[j].style) { continue; }

            const propertyValue = styleSheet.cssRules[j].style[property.name];
            if (shouldRemovePropertyForGivenValue(property, propertyValue)) {
                const removedValue = styleSheet.cssRules[j].style?.removeProperty(property.name);
                if (removedValue) {
                    console.info(`[FIX_EMAIL_STYLE] Remove property ${property.name} from style tag.`);
                }
            }
        }
    }
}

function shouldRemovePropertyForGivenValue(property, value) {
    return property.values.length === 0 || property.values.includes(value.toLowerCase().trim())
}
