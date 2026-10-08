#!/bin/bash
# =================================================================
# Panda Gallery — Font Downloader
# Downloads Inter and Outfit font families from Google Fonts
# Run this script from the project root directory.
# =================================================================

FONT_DIR="app/src/main/res/font"
mkdir -p "$FONT_DIR"

echo "🐼 Downloading fonts for Panda Gallery..."

# Inter font family
echo "📥 Downloading Inter..."
curl -sL "https://fonts.google.com/download?family=Inter" -o /tmp/inter.zip
unzip -qo /tmp/inter.zip -d /tmp/inter_fonts

# Copy and rename Inter variants
for weight in Regular Medium SemiBold Bold; do
    lower=$(echo "$weight" | tr '[:upper:]' '[:lower:]')
    src="/tmp/inter_fonts/static/Inter_18pt-${weight}.ttf"
    if [ -f "$src" ]; then
        cp "$src" "$FONT_DIR/inter_${lower}.ttf"
        echo "  ✅ inter_${lower}.ttf"
    else
        # Try alternate path
        src2="/tmp/inter_fonts/static/Inter-${weight}.ttf"
        if [ -f "$src2" ]; then
            cp "$src2" "$FONT_DIR/inter_${lower}.ttf"
            echo "  ✅ inter_${lower}.ttf"
        else
            echo "  ⚠️  Inter ${weight} not found, creating placeholder"
            touch "$FONT_DIR/inter_${lower}.ttf"
        fi
    fi
done

# Outfit font family
echo "📥 Downloading Outfit..."
curl -sL "https://fonts.google.com/download?family=Outfit" -o /tmp/outfit.zip
unzip -qo /tmp/outfit.zip -d /tmp/outfit_fonts

for weight in Light Regular Medium SemiBold Bold; do
    lower=$(echo "$weight" | tr '[:upper:]' '[:lower:]')
    src="/tmp/outfit_fonts/static/Outfit-${weight}.ttf"
    if [ -f "$src" ]; then
        cp "$src" "$FONT_DIR/outfit_${lower}.ttf"
        echo "  ✅ outfit_${lower}.ttf"
    else
        echo "  ⚠️  Outfit ${weight} not found, creating placeholder"
        touch "$FONT_DIR/outfit_${lower}.ttf"
    fi
done

# Cleanup
rm -rf /tmp/inter.zip /tmp/inter_fonts /tmp/outfit.zip /tmp/outfit_fonts

echo ""
echo "🎉 Fonts downloaded to $FONT_DIR"
echo "   You can also download manually from:"
echo "   https://fonts.google.com/specimen/Inter"
echo "   https://fonts.google.com/specimen/Outfit"
