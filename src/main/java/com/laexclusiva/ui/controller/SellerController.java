package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Seller;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public class SellerController {

    @FXML private TableView<Seller> sellersTable;
    @FXML private TableColumn<Seller, String> idColumn;
    @FXML private TableColumn<Seller, String> nameColumn;
    @FXML private TableColumn<Seller, String> phoneColumn;
    @FXML private TableColumn<Seller, String> addressColumn;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
    }

    public void showSellers(List<Seller> sellers) {
        sellersTable.getItems().setAll(sellers);
    }
}
