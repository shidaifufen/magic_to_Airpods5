# MagicToAirPods5 build script (Windows PowerShell)
# 依赖：JDK 17、Android SDK (build-tools 36 / platforms;android-36)、libxposed api 102
# 用法：先把 api-102.0.0.aar 解压出的 classes.jar 放到 $env:LIBXPOSED_JAR，
#       然后在本目录执行： pwsh -File build.ps1
$ErrorActionPreference = "Stop"

$JDK   = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\devtools\jdk-17.0.2" }
$SDK   = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\devtools\android-sdk" }
$BT    = Join-Path $SDK "build-tools\36.0.0"
$AJ    = Join-Path $SDK "platforms\android-36\android.jar"
$LIBX  = if ($env:LIBXPOSED_JAR) { $env:LIBXPOSED_JAR } else { "C:\devtools\libxposed-classes.jar" }
$OUT   = Join-Path $PSScriptRoot "release"
$WORK  = Join-Path $env:TEMP "magic2airpods5-build"

Remove-Item -Recurse -Force $WORK -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $WORK, "$WORK\classes", "$WORK\dex", "$WORK\res", $OUT -Force | Out-Null

# 1) 编译资源
& "$BT\aapt2.exe" compile --dir (Join-Path $PSScriptRoot "res") -o "$WORK\res\res.zip"

# 2) 链接 manifest + 资源
& "$BT\aapt2.exe" link --manifest (Join-Path $PSScriptRoot "AndroidManifest.xml") -I $AJ `
    --min-sdk-version 29 --target-sdk-version 36 -o "$WORK\base.apk" "$WORK\res\res.zip"

# 3) 编译 Java
$srcs = Get-ChildItem -Recurse (Join-Path $PSScriptRoot "src") -Filter *.java | ForEach-Object FullName
& "$JDK\bin\javac.exe" -encoding UTF-8 -cp "$AJ;$LIBX" -d "$WORK\classes" $srcs

# 4) dex
$cfs = Get-ChildItem -Recurse "$WORK\classes" -Filter *.class | ForEach-Object FullName
& "$JDK\bin\java.exe" -cp "$BT\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 29 --output "$WORK\dex" $cfs

# 5) 打包 classes.dex + META-INF/xposed
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$WORK\base.apk", "Update")
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$WORK\dex\classes.dex", "classes.dex", [IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()
$zip = [IO.Compression.ZipFile]::Open("$WORK\base.apk", "Update")
foreach ($f in @("module.prop", "scope.list", "java_init.list")) {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $PSScriptRoot "metainf\xposed\$f"),
        "META-INF/xposed/$f", [IO.Compression.CompressionLevel]::Optimal) | Out-Null
}
$zip.Dispose()

# 6) zipalign + 签名
& "$BT\zipalign.exe" -f 4 "$WORK\base.apk" "$WORK\aligned.apk"
$ks = Join-Path $PSScriptRoot "keystore\magic2airpods5.keystore"
& "$BT\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out (Join-Path $OUT "MagicToAirPods5-v1.0.apk") "$WORK\aligned.apk"

Write-Host "Done: $OUT\MagicToAirPods5-v1.0.apk"
