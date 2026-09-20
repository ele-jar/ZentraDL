# scripts/build-core.ps1 — Windows twin of build-core.sh (gomobile AAR).
param([string]$Out = "")
$ROOT = Split-Path (Split-Path $MyInvocation.MyCommand.Path -Parent) -Parent
if ($Out -eq "") { $Out = Join-Path $ROOT "android\app\libs" }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
  go install golang.org/x/mobile/cmd/gomobile@latest
  gomobile init
}
# NDK r28+ required for 16 KB page-size alignment (Play-enforced for Android 15+ targets).
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" `
  -o "$Out\libgopeed.aar" -target=android -androidapi 21 -javapkg="com.gopeed" `
  github.com/GopeedLab/gopeed/bind/mobile
Write-Host "AAR -> $Out\libgopeed.aar"
