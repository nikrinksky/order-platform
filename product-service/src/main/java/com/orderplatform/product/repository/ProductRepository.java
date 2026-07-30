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

    Page<Product> findByCategory(String category, Pageable pageable);

    @Query("{ '$or': [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    Page<Product> searchByText(String text, Pageable pageable);

    @Query("{ 'active': true, 'price': { $gte: ?0, $lte: ?1 } }")
    List<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);
}
