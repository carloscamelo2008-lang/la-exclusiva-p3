package com.laexclusiva.service;

import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.SellerRepository;
import java.util.List;
import java.util.Optional;

/**
 * Servicio encargado de la lógica de negocio y gestión de vendedores.
 */
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    /**
     * Registra un nuevo vendedor validando sus datos y lo almacena en memoria.
     * 
     * @param name Nombre del vendedor.
     * @param phone Teléfono de contacto.
     * @param address Dirección.
     * @return Objeto Seller recién registrado.
     */
    public Seller registerSeller(String name, String phone, String address) {
        Seller seller = new Seller(name, phone, address);
        sellerRepository.save(seller);
        return seller;
    }

    /**
     * Obtiene un vendedor por su ID.
     */
    public Optional<Seller> getSellerById(String id) {
        return sellerRepository.findById(id);
    }

    /**
     * Retorna la lista completa de vendedores registrados.
     */
    public List<Seller> getAllSellers() {
        return sellerRepository.findAll();
    }
}