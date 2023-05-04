function removeAllProperties() {
    const properties = [
        { name: 'position', values: ['absolute'] },
        // { name: '-webkit-text-size-adjust', values: [] } // For iOS only because they're based on webkit
    ];
    removeCSSProperty(properties)
}

function removeCSSProperty(properties) {
    // Remove properties from inline styles
    for (const property of properties) {
        const elementsWithInlineStyle = document.querySelectorAll(`[style*=${property.name}]`);
        for (const element of elementsWithInlineStyle) {
            if (property.values.length === 0 || property.values.includes(element.style[property.name].toLowerCase().trim())) {
                element.style[property.name] = null;
                console.info(`[FIX_EMAIL_STYLE] Remove property ${property.name} from inline style.`);
            }
        }
    }

    // Remove properties from style tag
    for (let i = 0; i < document.styleSheets.length; i++) {
        const styleSheet = document.styleSheets[i];
        for (let j = 0; j < styleSheet.cssRules.length; j++) {
            for (const property of properties) {
                if (!styleSheet.cssRules[j].style) { continue; }

                if (property.values.length === 0 || property.values.includes(styleSheet.cssRules[j].style[property.name].toLowerCase().trim())) {
                    const removedValue = styleSheet.cssRules[j].style?.removeProperty(property.name);
                    if (removedValue) {
                        console.info(`[FIX_EMAIL_STYLE] Remove property ${property.name} from style tag.`);
                    }
                }
            }
        }
    }
}
