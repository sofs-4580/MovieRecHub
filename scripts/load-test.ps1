# Gera trafego nos servicos para testar o dashboard do Grafana.
#
# Uso (na raiz do projeto, com o docker compose a correr):
#   .\scripts\load-test.ps1                      # 3 minutos de trafego normal
#   .\scripts\load-test.ps1 -Minutes 5           # durante 5 minutos
#   .\scripts\load-test.ps1 -WithErrors          # inclui pedidos com 4xx
#   .\scripts\load-test.ps1 -WithLlm             # inclui pedidos de recomendacao (lentos, usam o Ollama)

param(
	[int]$Minutes = 3,
	[switch]$WithErrors,
	[switch]$WithLlm
)

$frontend = "http://localhost:8080"
$movies = "http://localhost:8083"
$users = "http://localhost:8081"
$recommendations = "http://localhost:8084"

$queries = @("star", "love", "war", "nolan", "cameron", "the")

function Send-Request($method, $url, $body = $null) {
	try {
		if ($body) {
			Invoke-WebRequest -UseBasicParsing -Method $method -Uri $url -Body $body `
				-ContentType "application/json" -TimeoutSec 60 | Out-Null
		} else {
			Invoke-WebRequest -UseBasicParsing -Method $method -Uri $url -TimeoutSec 30 | Out-Null
		}
		return "ok"
	} catch {
		return "erro"
	}
}

$end = (Get-Date).AddMinutes($Minutes)
$round = 0
Write-Host "A gerar trafego durante $Minutes minuto(s)... (Ctrl+C para parar)"

while ((Get-Date) -lt $end) {
	$round++
	$results = @()

	# frontend (paginas)
	$results += Send-Request GET "$frontend/"
	$results += Send-Request GET "$frontend/login"
	$results += Send-Request GET "$frontend/movies"
	$results += Send-Request GET "$frontend/movies/$(Get-Random -Minimum 1 -Maximum 20)"

	# movie-service (API)
	$results += Send-Request GET "$movies/api/movies"
	$results += Send-Request GET "$movies/api/movies/$(Get-Random -Minimum 1 -Maximum 20)"
	$results += Send-Request GET "$movies/api/movies/search?query=$($queries | Get-Random)"
	$results += Send-Request GET "$movies/api/reviews/movie/$(Get-Random -Minimum 1 -Maximum 20)"

	if ($WithErrors) {
		# 404: filme que nao existe
		$results += Send-Request GET "$movies/api/movies/999999"
		# 401/400: login com password errada
		$results += Send-Request POST "$users/api/auth/login" '{"username":"nao_existe","password":"errada"}'
	}

	if ($WithLlm -and ($round % 5 -eq 0)) {
		# de 5 em 5 rondas, para nao entupir o Ollama
		$results += Send-Request POST "$recommendations/api/recommendations" '{"text":"a sci-fi movie with time travel"}'
	}

	$ok = ($results | Where-Object { $_ -eq "ok" }).Count
	Write-Host ("ronda {0}: {1}/{2} pedidos sem erro" -f $round, $ok, $results.Count)
	Start-Sleep -Milliseconds 500
}

Write-Host "Terminado."
