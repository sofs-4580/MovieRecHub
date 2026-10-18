import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// teste de carga ao frontend: aumenta o numero de clientes e mede throughput e latencia
// uso: java scripts/LoadTest.java http://IP_DO_WORKER:30510
public class LoadTest {

  static final int[] CLIENTES = {1, 2, 8, 32, 128, 512};
  static final int AQUECIMENTO = 10;
  static final int DURACAO = 60; // segundos medidos por fase
  static final int PAUSA = 20; // pausa entre fases (pra separarmos no grafana)

  static final int MAX_LLM = 3;

  static final String PASSWORD = "loadtest-password-2026";


  static String url;
  static List<HttpClient> clientes = new ArrayList<>(); // um por utilizador, cada um com os seus cookies
  static List<String> ids = new ArrayList<>();
  static List<String> generos = new ArrayList<>();
  static int[] pedidosLlm; // quantos pedidos ao ollama cada utilizador ja fez nesta fase
  static long inicioMedicao; // so se pede ao ollama depois do aquecimento, pra esses pedidos contarem

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("uso: java scripts/LoadTest.java http://IP_DO_WORKER:30510");
      return;
    }

    url = args[0];
    Locale.setDefault(Locale.ROOT);


    int maxClientes = CLIENTES[CLIENTES.length - 1];
    for (int i = 1; i <= maxClientes; i++) {
      clientes.add(entrar(String.format("loadtest_%02d", i)));
    }

    lerCatalogo();
    System.out.println(ids.size() + " filmes, " + generos.size() + " generos");

    List<String> linhasCsv = new ArrayList<>();

    for (int n : CLIENTES) {
      System.out.println(LocalTime.now().withNano(0) + " - fase com " + n + " clientes");
      linhasCsv.add(fase(n));
      Thread.sleep(PAUSA * 1000L);
    }

    System.out.println();
    System.out.println("clientes | pedidos | pedidos/s | p50 (ms) | p95 (ms) | erros (%)");
    for (String linha : linhasCsv) {
      System.out.println(linha);
    }
  }


  // cria a conta (se ja existir, o registo falha mas nao faz mal) e faz login
  static HttpClient entrar(String username) throws Exception {
    HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .cookieHandler(new CookieManager())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
 
    http.send(formulario("/register", "username=" + username + "&password=" + PASSWORD
        + "&confirmPassword=" + PASSWORD + "&favoriteGenres=DRAMA&favoriteGenres=COMEDY"),
        HttpResponse.BodyHandlers.discarding());

    // um login bem feito responde com um redirect para /recommendations
    int estado = http.send(formulario("/login", "username=" + username + "&password=" + PASSWORD),
        HttpResponse.BodyHandlers.discarding()).statusCode();
    if (estado != 302) {
      throw new RuntimeException("login falhou para " + username + " (HTTP " + estado + ")");
    }

    return http;
  }
 

  // vai buscar os ids dos filmes e os generos a pagina /movies, para os pedidos serem validos
  static void lerCatalogo() throws Exception {
    String html = clientes.get(0).send(pedido("/movies"), HttpResponse.BodyHandlers.ofString()).body();

    Matcher i = Pattern.compile("href=\"/movies/(\\d+)\"").matcher(html);
    while (i.find()) {
      ids.add(i.group(1));
    }
    Matcher g = Pattern.compile("href=\"/movies\\?genre=([A-Z_]+)\"").matcher(html);
    while (g.find()) {
      generos.add(g.group(1));
    }
  }

  // corre uma fase com n clientes e devolve a linha do csv
  static String fase(int n) throws InterruptedException {
    long inicio = System.nanoTime() + AQUECIMENTO * 1_000_000_000L;
    long fim = inicio + DURACAO * 1_000_000_000L;

    List<Double> tempos = Collections.synchronizedList(new ArrayList<>());
    int[] erros = {0};

    pedidosLlm = new int[n];
    inicioMedicao = inicio;

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      HttpClient http = clientes.get(i);
      int user = i;
      Thread t = new Thread(() -> cliente(http, user, inicio, fim, tempos, erros));
      t.start();
      threads.add(t);
    }
    for (Thread t : threads) {
      t.join();
    }

    List<Double> ordenados = new ArrayList<>(tempos);
    Collections.sort(ordenados);
    int pedidos = ordenados.size() + erros[0];
    double throughput = ordenados.size() / (double) DURACAO;
    double percErros = pedidos == 0 ? 0 : 100.0 * erros[0] / pedidos;

    System.out.printf("  %.1f pedidos/s, p50 %.1f ms, p95 %.1f ms, erros %.2f%%%n",
        throughput, percentil(ordenados, 50), percentil(ordenados, 95), percErros);
    return String.format("%8d | %7d | %9.1f | %8.1f | %8.1f | %8.2f",
        n, pedidos, throughput, percentil(ordenados, 50), percentil(ordenados, 95), percErros);
  }

  // cada cliente faz um pedido de cada vez, sem esperar entre pedidos
  static void cliente(HttpClient http, int user, long inicio, long fim, List<Double> tempos, int[] erros) {
    Random random = new Random();
    while (System.nanoTime() < fim) {
      long t0 = System.nanoTime();
      boolean ok;
      try {
        ok = http.send(caminho(random, user), HttpResponse.BodyHandlers.discarding())
            .statusCode() == 200;
      } catch (Exception e) {
        ok = false;
      }
      long t1 = System.nanoTime();

      // so conta o que comecou depois do aquecimento e acabou antes do fim
      if (t0 >= inicio && t1 <= fim) {
        if (ok) {
          tempos.add((t1 - t0) / 1_000_000.0);
        } else {
          synchronized (erros) {
            erros[0]++;
          }
        }
      }
    }
  }

static HttpRequest caminho(Random random, int user) {
    double r = random.nextDouble();
    if (r < 0.25) {
      return pedido("/movies"); // MovieService
    } else if (r < 0.50) {
      return pedido("/movies/" + ids.get(random.nextInt(ids.size()))); // MovieService (filme + reviews)
    } else if (r < 0.60) {
      return pedido("/lists"); // MovieService (listas do utilizador)
    } else if (r < 0.75) {
      return pedido("/profiles"); // ProfileService (perfis dos outros)
    } else if (r < 0.85) {
      return pedido("/profile"); // ProfileService (o proprio perfil)
    } else if (r < 0.95 || pedidosLlm[user] >= MAX_LLM || System.nanoTime() < inicioMedicao) {
      return pedido("/recommendations"); // RecommendationService (recomendacoes ja preparadas)
    } else {
      // RecommendationService + Ollama
      pedidosLlm[user]++;
      return formulario("/recommendations", "text=" + URLEncoder.encode("a comedy movie", StandardCharsets.UTF_8));
   }
}

  static HttpRequest pedido(String caminho) {
    return HttpRequest.newBuilder(URI.create(url + caminho))
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
  }

  //AAAAAAAAAA

  static HttpRequest formulario(String caminho, String corpo) {
    return HttpRequest.newBuilder(URI.create(url + caminho))
        .timeout(Duration.ofSeconds(150))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(corpo))
        .build();
  }

  static double percentil(List<Double> ordenados, int p) {
    if (ordenados.isEmpty()) {
      return 0;
    }
    int i = (int) Math.ceil(p / 100.0 * ordenados.size()) - 1;
    return ordenados.get(Math.max(0, i));
  }
}