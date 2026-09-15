package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.domain.model.Product

/**
 * In-memory lookup of products already fetched from the paginated grid. The mock API (like the
 * real Shopify Storefront API surface described in the brief) exposes no "get product by id"
 * endpoint, so navigation to the detail screen passes only an id and this cache resolves it
 * from whatever page has already been loaded.
 */
interface ProductCache {
    fun put(products: List<Product>)
    fun get(id: String): Product?
}
