$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$file = Join-Path $PSScriptRoot "sample-arquitectura.txt"
Write-Host "Cargando documento de ejemplo..."
$upload = curl.exe -s -F "file=@$file;type=text/plain" -F "titulo=Guia de arquitectura" -F "autor=Ana Ruiz" -F "categoria=arquitectura" -F "etiquetas=microservicios,elasticsearch" -F "version=1.0" "$base/api/v1/documents"
Write-Host $upload
Start-Sleep -Seconds 8
Write-Host "Busqueda:"
curl.exe -s "$base/api/v1/search?q=arquitectura"
Write-Host ""
