import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final int requestLimit;
    private final long timeIntervalMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.schedule(this::reset, timeIntervalMillis, timeUnit);
        service.shutdown();
    }

    private void reset() {
        requestCount.setRelease(0);
    }

    public void createDocument(Object document, String signature) throws InterruptedException, IOException {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                condition.await();
            }
            requestCount.incrementAndGet();
            sendRequest(document, signature);
        } finally {
            lock.unlock();
        }
    }

    private void sendRequest(Object document, String signature) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Запрос отправлен: " + document + ", подпись: " + signature);
    }
}