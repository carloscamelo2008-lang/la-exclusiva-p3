package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class PortfolioController {

    @FXML private TableView<Customer> portfolioTable;
    @FXML private TableColumn<Customer, String> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, BigDecimal> balanceColumn;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        balanceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getBalance()));
        balanceColumn.setCellFactory(column -> new TableCell<>() {
            private final NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));

            @Override
            protected void updateItem(BigDecimal balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                    return;
                }
                format.setMaximumFractionDigits(Math.max(2, balance.scale()));
                format.setMinimumFractionDigits(2);
                setText("$ " + format.format(balance));
            }
        });
    }

    public void showDebtors(List<Customer> debtors) {
        portfolioTable.getItems().setAll(debtors);
    }
}
