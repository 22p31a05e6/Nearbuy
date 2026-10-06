package com.Echo.NearBuy.cart.service;

import com.Echo.NearBuy.cart.dto.AddCartItemRequest;
import com.Echo.NearBuy.cart.entity.Cart;
import com.Echo.NearBuy.cart.entity.CartItem;
import com.Echo.NearBuy.cart.repository.CartItemRepository;
import com.Echo.NearBuy.cart.repository.CartRepository;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.product.entity.Product;
import com.Echo.NearBuy.product.entity.ProductUnit;
import com.Echo.NearBuy.product.repository.ProductRepository;
import com.Echo.NearBuy.product.repository.ProductUnitRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductUnitRepository productUnitRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productUnitRepository = productUnitRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartItem addItem(Long customerId, AddCartItemRequest request) {
        requireCustomer(customerId);
        ProductUnit productUnit = productUnitRepository.findById(request.productUnitId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product unit not found: " + request.productUnitId()));
        Product product = productRepository.findById(productUnit.getProductId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found: " + productUnit.getProductId()));
        if (!product.isActive()) {
            throw new IllegalStateException("Inactive products cannot be added to the cart");
        }

        Cart cart = cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setCustomerId(customerId);
            return cartRepository.save(newCart);
        });
        List<CartItem> existingItems = cartItemRepository.findByCartId(cart.getId());
        for (CartItem existingItem : existingItems) {
            ProductUnit existingUnit = productUnitRepository.findById(existingItem.getProductUnitId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product unit not found: " + existingItem.getProductUnitId()));
            Product existingProduct = productRepository.findById(existingUnit.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product not found: " + existingUnit.getProductId()));
            if (!existingProduct.getShopId().equals(product.getShopId())) {
                throw new IllegalArgumentException("A cart can contain items from only one shop");
            }
        }

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductUnitId(cart.getId(), productUnit.getId())
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setCartId(cart.getId());
                    newItem.setProductUnitId(productUnit.getId());
                    newItem.setQuantity(0);
                    return newItem;
                });
        cartItem.setQuantity(Math.addExact(cartItem.getQuantity(), request.quantity()));
        return cartItemRepository.save(cartItem);
    }

    public Cart getCart(Long customerId) {
        requireCustomer(customerId);
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Cart not found for customer: " + customerId));
    }

    public List<CartItem> getItems(Long customerId) {
        return cartItemRepository.findByCartId(getCart(customerId).getId());
    }

    @Transactional
    public void removeItem(Long customerId, Long cartItemId) {
        Cart cart = getCart(customerId);
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new EntityNotFoundException("Cart item not found: " + cartItemId));
        if (!cartItem.getCartId().equals(cart.getId())) {
            throw new EntityNotFoundException("Cart item not found: " + cartItemId);
        }
        cartItemRepository.delete(cartItem);
    }

    private void requireCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerId));
        if (!customer.isEnabled() || customer.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only an enabled customer can manage a cart");
        }
    }
}
