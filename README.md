# PrintBridge – core modules (part 1)
Included: Gradle module config, manifest (USB attach filter, BT permissions for API 31+),
USB Host transport (permission, interface/bulk endpoint discovery, chunked writes, unplug detection),
TSPL builder (SIZE/GAP/BLINE/DIRECTION/REFERENCE/CLS/TEXT/BARCODE/QRCODE/BITMAP/PRINT/SET PEEL/SET CUTTER),
ESC/POS builder (text, raster, QR, barcode, feed, cut), 1bpp raster + dithering.
Next parts: Bluetooth transport, PrintEngine, PDF/Image renderers, DataStore repos, Compose UI + Hindi/English, PrintService.
Test with TSC TE210: plug via OTG, VID 0x1203, language = TSPL, label 75x75 mm.
