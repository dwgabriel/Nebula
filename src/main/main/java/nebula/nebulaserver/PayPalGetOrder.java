package nebula.nebulaserver;

import com.braintreepayments.http.HttpResponse;
import com.braintreepayments.http.serializer.Json;
import com.paypal.orders.Order;
import com.paypal.orders.OrdersGetRequest;
import org.json.JSONObject;

import java.io.IOException;

public class PayPalGetOrder extends PayPalClient {

    public static void main(String[] args) {
        try {
            new PayPalGetOrder().getOrder("");

        } catch (com.braintreepayments.http.exceptions.HttpException e) {
            System.out.println(e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getOrder (String orderId) throws IOException {
        OrdersGetRequest request = new OrdersGetRequest(orderId);
        HttpResponse<Order> response = client().execute(request);
        System.out.println("Full Response Body : ");
        System.out.println(new JSONObject(new Json().serialize(response.result())).toString(4));
    }

}
