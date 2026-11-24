package com.laexclusiva.service;

import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.SellerRepositoryInterface;
import java.util.List;
import java.util.Optional;

public class SellerService {

    private final SellerRepositoryInterface sellerRepository;

    public SellerService(SellerRepositoryInterface sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    public Seller registerSeller(String name, String phone, String address) {
        Seller seller = new Seller(name, phone, address);
        sellerRepository.save(seller);
        return seller;
    }

    public Optional<Seller> getSellerById(String id) {
        return sellerRepository.findById(id);
    }

    public void setActive(String id, boolean active) {
        sellerRepository.setActive(id, active);
    }

    public List<Seller> getActiveSellers() {
        return sellerRepository.findAll().stream().filter(Seller::isActive).toList();
    }

    public List<Seller> getAllSellers() {
        return sellerRepository.findAll();
    }
}