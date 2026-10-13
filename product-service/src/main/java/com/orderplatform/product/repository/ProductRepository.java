package com.orderplatform.product.repository;

import com.orderplatform.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByActive(boolean active);

    Page<Product> findByActive(boolean active, Pageable pageable);

    Page<Product> findByCategory(String category, Pageable pageable);

    /** Published products only, for callers that must not see the hidden ones. */
    Page<Product> findByCategoryAndActive(String category, boolean active, Pageable pageable);

    /**
     * Text search over every product.
     *
     * <p>The {@code regex} argument must already be escaped by the caller: an unescaped user
     * string lets a client inject regex operators ({@code .*}, a nested quantifier) and turn a
     * catalogue search into a full scan of the collection.
     */
    @Query("{ '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    Page<Product> searchByText(String regex, Pageable pageable);

    /** Text search restricted to published products. The {@code regex} argument must be escaped. */
    @Query("{ 'active': true, '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    Page<Product> searchActiveByText(String regex, Pageable pageable);

    @Query("{ 'active': true, 'price': { $gte: ?0, $lte: ?1 } }")
    List<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    /** Price range over every product, including the hidden ones. */
    @Query("{ 'price': { $gte: ?0, $lte: ?1 } }")
    List<Product> findByPriceRangeIncludingHidden(BigDecimal minPrice, BigDecimal maxPrice);
}
