package nebula.nebulaserver;

import com.braintreepayments.http.HttpResponse;
import com.paypal.orders.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PayPalCreate extends PayPalClient {

    public static void main(String[] args) {
        try {
            HttpResponse<Order> response = new PayPalCreate().createOrder(true);

            PayPalGetOrder order = new PayPalGetOrder();
            order.getOrder(response.result().id());

//            PayPalCapture capture = new PayPalCapture();
//            capture.captureOrder(response.result().id(), true);

        } catch (com.braintreepayments.http.exceptions.HttpException e) {
            System.out.println(e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HttpResponse<Order> createOrder (boolean debug) throws IOException {
        OrdersCreateRequest request = new OrdersCreateRequest();
        request.prefer("return=representation");
        request.requestBody(buildRequestBody());

        HttpResponse<Order> response = client().execute(request);
        if (debug) {
            if (response.statusCode() == 201) {
                System.out.println("Status Code: " + response.statusCode());
                System.out.println("Status: " + response.result().status());
                System.out.println("Order ID: " + response.result().id());
                System.out.println("Intent: " + response.result().intent());
                System.out.println("Links: ");
                for (LinkDescription link : response.result().links()) {
                    System.out.println("\t" + link.rel() + ": " + link.href() + "\tCall Type: " + link.method());
                }
                System.out.println("Total Amount: " + response.result().purchaseUnits().get(0).amount().currencyCode()
                        + " " + response.result().purchaseUnits().get(0).amount().value());
            }
        }
        return response;
    }

    private OrderRequest buildRequestBody() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.intent("CAPTURE");

        ApplicationContext applicationContext = new ApplicationContext().brandName("Nebula Technologies.").landingPage("BILLING")
                .shippingPreference("SET_PROVIDED_ADDRESS");
        orderRequest.applicationContext(applicationContext);

        List<PurchaseUnitRequest> purchaseUnitRequests = new ArrayList<PurchaseUnitRequest>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest().referenceId("789456")
                .description("Render")
                .amount(new AmountWithBreakdown().currencyCode("USD").value("100.00")
                        .breakdown(new AmountBreakdown().itemTotal(new Money().currencyCode("USD").value("100.00"))     // Total Item Value (w/ other Costs)
                                .shipping(new Money().currencyCode("USD").value("0.00"))
                                .handling(new Money().currencyCode("USD").value("0.00"))
                                .taxTotal(new Money().currencyCode("USD").value("0.00"))
                                .shippingDiscount(new Money().currencyCode("USD").value("0.00"))))
                .items(new ArrayList<Item>() {
                    {
                        add(new Item().name("Render 1").description("456123")
                                .unitAmount(new Money().currencyCode("USD").value("100.00"))
                                .tax(new Money().currencyCode("USD").value("0.00")).quantity("1"));
                    }
                })
                .shipping(new ShippingDetails().name(new Name().fullName("Daryl Wong"))
                        .addressPortable(new AddressPortable().addressLine1("B-36-1, Vogue Suites One").addressLine2("No.3 Jalan Bangsar")
                                .adminArea2("Kuala Lumpur").adminArea1("KL").postalCode("59200").countryCode("MY")));
        purchaseUnitRequests.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnitRequests);
        return orderRequest;
    }

}
