#!/bin/bash
set -e
cd "$(dirname "$0")"; S=1080
rsvg-convert -w $S -h $S full.svg -o pv/full.png; rsvg-convert -w $S -h $S mono.svg -o pv/mono_raw.png
for m in square circle squircle; do magick -size ${S}x${S} xc:'#EEEEEE' pv/full.png $m.png -composite -crop 720x720+180+180 +repage -resize 360 pv/$m.png; done
magick -size ${S}x${S} xc:'#1F3A4D' \( pv/mono_raw.png -channel RGB -fill '#C8E6FF' -colorize 100 +channel \) -compose over -composite pv/themedfull.png
magick -size ${S}x${S} xc:'#EEEEEE' pv/themedfull.png circle.png -composite -crop 720x720+180+180 +repage -resize 360 pv/themed.png
magick -size ${S}x${S} xc:'#E8F0E0' \( pv/mono_raw.png -channel RGB -fill '#2D4A1E' -colorize 100 +channel \) -compose over -composite pv/themedfull2.png
magick -size ${S}x${S} xc:'#EEEEEE' pv/themedfull2.png squircle.png -composite -crop 720x720+180+180 +repage -resize 360 pv/themed_light.png
magick pv/full.png -fill none -stroke red -strokewidth 4 -draw "circle 540,540 540,210" -stroke yellow -draw "rectangle 180,180 899,899" -resize 360 pv/safezone.png
magick pv/square.png pv/circle.png pv/squircle.png pv/themed.png pv/themed_light.png pv/safezone.png -bordercolor '#EEEEEE' -border 12 +append pv/sheet.png
# legacy + play
rsvg-convert -w 2304 -h 2304 full.svg -o big.png; magick big.png -crop 1536x1536+384+384 +repage tile1536.png
magick -size 1536x1536 xc:none tile1536.png lsq.png -composite lsq_icon.png
magick -size 1536x1536 xc:none tile1536.png lrd.png -composite lrd_icon.png
for p in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do d=${p%%:*}; s=${p#*:}; mkdir -p legacy/mipmap-$d
  magick lsq_icon.png -filter Lanczos -resize ${s}x${s} -define webp:lossless=true legacy/mipmap-$d/ic_launcher.webp
  magick lrd_icon.png -filter Lanczos -resize ${s}x${s} -define webp:lossless=true legacy/mipmap-$d/ic_launcher_round.webp; done
magick tile1536.png -filter Lanczos -resize 512x512 -strip PNG24:play512.png; cp play512.png pv/play512.png
magick \( legacy/mipmap-xxxhdpi/ic_launcher.webp -background '#EEEEEE' -flatten \) \( legacy/mipmap-xxxhdpi/ic_launcher_round.webp -background '#EEEEEE' -flatten \) \( legacy/mipmap-mdpi/ic_launcher.webp -background '#EEEEEE' -flatten -gravity center -extent 192x192 \) \( legacy/mipmap-mdpi/ic_launcher_round.webp -background '#EEEEEE' -flatten -gravity center -extent 192x192 \) \( play512.png -resize 192 \) -bordercolor '#EEEEEE' -border 8 +append pv/legacy.png
# tv banner + qs tile
rsvg-convert -w 540 -h 540 fg.svg -o pv/fg540.png; magick -size 960x540 gradient:"#3AA0FF-#0088FF" pv/fg540.png -gravity center -composite pv/tv_banner.png
rsvg-convert -w 96 -h 96 tile.svg -o t96.png; magick -size 400x160 xc:'#2B2F36' -fill '#3A4A5C' -draw "roundrectangle 16,32 384,128 48,48" t96.png -geometry 72x72+40+44 -composite -fill white -pointsize 28 -annotate +130+92 'Shoutkit' pv/qs_tile.png
magick pv/tv_banner.png -resize 400 tvs.png; magick tvs.png \( pv/qs_tile.png -background '#EEEEEE' -gravity west -splice 16x0 \) -background '#EEEEEE' -gravity center +append pv/extras.png
# fidelity: iOS original vs redraw on same bg
magick -size 1024x1024 gradient:"#3AA0FF-#0088FF" "../fdroid/ios/HolmdelApp/Holmdel/Squan-AppIcon.icon/Assets/Radio beacon.png" -composite -resize 360 ios.png
rsvg-convert -w 1536 -h 1536 full.svg | magick - -crop 1024x1024+256+256 +repage -resize 360 new.png
magick ios.png new.png -bordercolor '#EEEEEE' -border 8 +append pv/compare.png
mkdir -p pub; cp pv/sheet.png pub/adaptive-masks.png; cp pv/legacy.png pub/legacy-and-play.png; cp pv/extras.png pub/tv-banner-and-qs-tile.png; cp pv/play512.png pub/play-512.png; cp pv/compare.png pub/ios-vs-android.png
