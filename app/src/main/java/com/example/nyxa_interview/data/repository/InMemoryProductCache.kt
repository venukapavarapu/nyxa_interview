package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.domain.model.Product
import com.example.nyxa_interview.domain.repository.ProductCache
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryProductCache @Inject constructor() : ProductCache {

    private val products = ConcurrentHashMap<String, Product>()

    override fun put(products: List<Product>) {
        products.forEach { this.products[it.id] = it }
    }

    override fun get(id: String): Product? = products[id]
}
