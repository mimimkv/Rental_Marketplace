package com.devminds.rentify.service;

import com.devminds.rentify.entity.Item;
import com.devminds.rentify.entity.User;
import com.devminds.rentify.repository.ItemRepository;
import com.devminds.rentify.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Capability;
import com.stripe.model.Product;
import com.stripe.model.Token;
import com.stripe.model.checkout.Session;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.TokenCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.issuing.CardholderCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URL;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StripeService {

    @Value("${stripe-key}")
    private String key;

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    private static final String[] HEADERS_TO_TRY = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"};

    public String getClientIpAddress(HttpServletRequest request) {
        for (String header : HEADERS_TO_TRY) {
            String ip = request.getHeader(header);
            if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    public Account createStripeAccount(HttpServletRequest httpServletRequest, Long userId) throws StripeException {
        Stripe.apiKey = key;

        User principal = userRepository.findById(userId).orElse(null);

        TokenCreateParams tokenParams =
                TokenCreateParams.builder()
                        .setBankAccount(
                                TokenCreateParams.BankAccount.builder()
                                        .setCountry("BG")
                                        .setCurrency("bgn")
                                        .setAccountHolderName(principal.getFirstName() + " " + principal.getLastName())
                                        .setAccountHolderType(
                                                TokenCreateParams.BankAccount.AccountHolderType.INDIVIDUAL
                                        )
                                        .setAccountNumber(principal.getIban())
                                        .build()
                        )
                        .build();
        Token token = Token.create(tokenParams);

        AccountCreateParams params =
                AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.CUSTOM)
                        .setCountry("BG")
                        .setEmail(principal.getEmail())
                        .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                        .setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                                .setMcc("5931")
                                .setProductDescription("marketplaces").build())
                        .setIndividual(AccountCreateParams.Individual.builder()
                                .setFirstName(principal.getFirstName())
                                .setLastName(principal.getLastName())
                                .setDob(AccountCreateParams.Individual.Dob.builder().setYear(2000L)
                                        .setMonth(9L)
                                        .setDay(9L).build())
                                .setAddress(AccountCreateParams.Individual.Address.builder()
                                        .setCountry("BG")
                                        .setCity(principal.getAddress().getCity())
                                        .setLine1(principal.getAddress().getStreet() + " " + principal.getAddress().getStreetNumber())
                                        .setState(principal.getAddress().getCity())
                                        .setPostalCode(principal.getAddress().getPostCode()).build())
                                .setEmail(principal.getEmail())
                                .setPhone(principal.getPhoneNumber())
                                .build())
                        .setTosAcceptance(AccountCreateParams.TosAcceptance.builder()
                                .setIp(getClientIpAddress(httpServletRequest))
                                .setDate(new Date().getTime() / 1000)
                                .build())

                        .setExternalAccount(token.getId())

                        .setCapabilities(
                                AccountCreateParams.Capabilities.builder()
                                        .setCardPayments(
                                                AccountCreateParams.Capabilities.CardPayments.builder()
                                                        .setRequested(true)
                                                        .build()
                                        )
                                        .setTransfers(
                                                AccountCreateParams.Capabilities.Transfers.builder()
                                                        .setRequested(true)
                                                        .build()
                                        )
                                        .build()
                        ).build();


        if(principal.getStripeAccountId() == null){
            Account account = Account.create(params);
            principal.setStripeAccountId(account.getIndividual().getAccount());
            account.setPayoutsEnabled(true);
            userRepository.save(principal);
            return account;
        }

        Account account = Account.retrieve(principal.getStripeAccountId());
        account.setPayoutsEnabled(true);
        return account;
    }


    public Product createProduct(Item item, List<URL> pictureUrls) throws StripeException {
        Stripe.apiKey = key;

        ProductCreateParams params =
                ProductCreateParams.builder().setName(item.getName())
                        .setDefaultPriceData(ProductCreateParams.DefaultPriceData.builder().setUnitAmount(item.getPrice().longValueExact() * 100L).setCurrency("usd").build())
                        .addImage(pictureUrls.get(0).toString()).build();


        Product product = Product.create(params);

        item.setItemStripeId(product.getDefaultPrice());
        itemRepository.save(item);

        return product;
    }


    public String createCheckoutSession(Long itemId) throws StripeException {
        Stripe.apiKey = key;

        Item item = itemRepository.findById(itemId).orElse(null);
        User itemOwner = userRepository.findById(item.getUser().getId()).orElse(null);

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(item.getItemStripeId())
                                        .setQuantity(1L)
                                        .build()
                        )
                        .setPaymentIntentData(
                                SessionCreateParams.PaymentIntentData.builder()
                                        .setApplicationFeeAmount(1230L)
                                        .setTransferData(
                                                SessionCreateParams.PaymentIntentData.TransferData.builder()
                                                        .setDestination(itemOwner.getStripeAccountId())
                                                        .build()
                                        )
                                        .build()
                        )
                        .setSuccessUrl("http://localhost:3000/")
                        .setCancelUrl("https://example.com/cancel")
                        .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

}
