# 下载 Sherpa-ONNX wenetspeech KWS 模型到 app assets
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$assetsDir = Join-Path $root "app\src\main\assets\sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
$keywords = Join-Path $assetsDir "keywords.txt"
$tar = Join-Path $env:TEMP "sherpa-kws-wenetspeech.tar.bz2"
$url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2"

New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
Write-Host "Downloading $url ..."
Invoke-WebRequest -Uri $url -OutFile $tar
Write-Host "Extracting to $assetsDir ..."
tar -xjf $tar -C $assetsDir --strip-components=1
python -c "
path = r'$keywords'
lines = [
'x i\u01ceo y \u00fa t \u00f3ng x u\u00e9 @\u5c0f\u9c7c\u540c\u5b66',
'x i\u01ceo y \u00fa x i\u01ceo y \u00fa @\u5c0f\u9c7c\u5c0f\u9c7c',
'm \u0101o m \u012b m \u0101o m \u012b @\u732b\u5499\u732b\u5499',
'n \u01d0 h \u01ceo j \u016bn g \u0113 @\u4f60\u597d\u519b\u54e5',
'd \u00e0n g \u0113 d \u00e0n g \u0113 @\u86cb\u54e5\u86cb\u54e5',
'x i\u01ceo \u00e0i t \u00f3ng x u\u00e9 @\u5c0f\u7231\u540c\u5b66',
'n \u01d0 h \u01ceo w \u00e8n w \u00e8n @\u4f60\u597d\u95ee\u95ee',
'x i\u01ceo y \u00ec x i\u01ceo y \u00ec @\u5c0f\u827a\u5c0f\u827a',
'x i\u01ceo m \u01d0 x i\u01ceo m \u01d0 @\u5c0f\u7c73\u5c0f\u7c73',
'l \u00edn m \u011b i l \u00ec @\u6797\u7f8e\u4e3d',
'n \u01d0 h \u01ceo x \u012b x \u012b @\u4f60\u597d\u897f\u897f',
]
open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
"
Write-Host "Done. Model files:"
Get-ChildItem $assetsDir
