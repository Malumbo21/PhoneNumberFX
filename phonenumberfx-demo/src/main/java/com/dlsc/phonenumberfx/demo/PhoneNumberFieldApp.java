package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;

import java.util.function.Function;

public class PhoneNumberFieldApp extends Application {

    private static final Function<Object, String> COUNTRY_CODE_CONVERTER = c -> {
        if (c == null) {
            return null;
        }
        PhoneNumberField.CountryCallingCode code = (PhoneNumberField.CountryCallingCode) c;
        return "(" + code.phonePrefix() + ") " + code;
    };

    @Override
    public void start(Stage stage) {
        PhoneNumberField field = new PhoneNumberField();

        VBox controls = new VBox(10);
        addControl("Available Countries", availableCountriesSelector(field), controls);
        addControl("Preferred Countries", preferredCountriesSelector(field), controls);
        addControl("Disable Country", disableCountryCheck(field), controls);
        addControl("", clearButton(field), controls);

        VBox fields = new VBox(10);
        addField(fields, "Country Code", field.countryCallingCodeProperty(), COUNTRY_CODE_CONVERTER);
        addField(fields, "Raw PhoneNumber", field.rawPhoneNumberProperty());
        addField(fields, "E164 PhoneNumber", field.e164PhoneNumberProperty());
        addField(fields, "National PhoneNumber", field.nationalPhoneNumberProperty());

        VBox vBox = new VBox(20);
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(controls, new Separator(), field, new Separator(), fields);

        stage.setTitle("PhoneNumberField2");
        stage.setScene(new Scene(vBox, 500, 500));
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    private Node availableCountriesSelector(PhoneNumberField field) {
        CheckBox allCountries = new CheckBox("All");

        CheckComboBox<PhoneNumberField.CountryCallingCode> comboBox = new CheckComboBox<>();
        comboBox.getItems().addAll(PhoneNumberField.CountryCallingCode.Defaults.values());
        comboBox.setPrefWidth(250);
        comboBox.getCheckModel().getCheckedItems().addListener((InvalidationListener) observable -> field.getAvailableCountryCodes().setAll(comboBox.getCheckModel().getCheckedItems()));
        comboBox.getCheckModel().checkAll();

        allCountries.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                comboBox.getCheckModel().checkAll();
                comboBox.setDisable(true);
            } else {
                comboBox.getCheckModel().clearChecks();
                comboBox.setDisable(false);
            }
        });

        allCountries.setSelected(true);

        HBox box = new HBox(10);
        box.getChildren().addAll(allCountries, comboBox);

        return box;
    }

    private Node preferredCountriesSelector(PhoneNumberField view) {
        CheckComboBox<PhoneNumberField.CountryCallingCode> comboBox = new CheckComboBox<>();
        comboBox.getItems().addAll(PhoneNumberField.CountryCallingCode.Defaults.values());
        comboBox.setPrefWidth(300);
        Bindings.bindContent(view.getPreferredCountryCodes(), comboBox.getCheckModel().getCheckedItems());
        return comboBox;
    }

    private Node disableCountryCheck(PhoneNumberField field) {
        CheckBox check = new CheckBox();
        check.selectedProperty().bindBidirectional(field.disableCountryCodeProperty());
        return check;
    }

    private Node clearButton(PhoneNumberField field) {
        Button clear = new Button("Clear all");
        clear.setOnAction(evt -> field.setRawPhoneNumber(null));
        return clear;
    }

    private void addControl(String name, Node control, VBox controls) {
        Label label = new Label(name);
        label.setPrefWidth(150);
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(label, control);
        HBox.setHgrow(label, Priority.NEVER);
        HBox.setHgrow(control, Priority.ALWAYS);
        controls.getChildren().add(hBox);
    }

    private void addField(VBox fields, String label, ObservableValue property) {
        addField(fields, label, property, null);
    }

    private void addField(VBox fields, String label, ObservableValue property, Function<Object, String> converter) {
        Label value = new Label();
        if (converter == null) {
            value.textProperty().bind(Bindings.convert(property));
        } else {
            value.textProperty().bind(Bindings.createStringBinding(() -> converter.apply(property.getValue()), property));
        }

        Label myLabel = new Label(label + ": ");
        myLabel.setPrefWidth(150);

        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(myLabel, value);
        HBox.setHgrow(myLabel, Priority.NEVER);
        HBox.setHgrow(value, Priority.ALWAYS);

        fields.getChildren().add(hBox);
    }

    public static void main(String[] args) {
        launch();
    }

}
