package nebula.nebulaserver;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;

public class PayPalClient {

    private PayPalEnvironment environment = new PayPalEnvironment.Sandbox("Ad4KZiB77lbTNVQbcpg8EvFquCKZTaxaz_aJR58wz_LXztoiuG3Dbg2dTKXL4Q2xT6PyWUwpMl6glwHH", "ED2ygXtc4ue0EeMa7-DHVWVyTrbgYL8srt-VkWYWo6555BEiQD6vW5Xn8EJPZrTgAKygTtpXIsvEERyq");

    PayPalHttpClient client = new PayPalHttpClient(environment);

    public PayPalHttpClient client() {
        return this.client;
    }
}
