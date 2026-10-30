package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "coin_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private CoinPackage coinPackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    public CoinOrder(
            User user,
            CoinPackage coinPackage,
            PaymentMode paymentMode
    ) {
        this.user = user;
        this.coinPackage = coinPackage;
        this.paymentMode = paymentMode;
    }
}
