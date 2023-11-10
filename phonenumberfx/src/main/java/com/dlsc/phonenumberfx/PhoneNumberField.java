package com.dlsc.phonenumberfx;

import com.dlsc.phonenumberfx.skins.PhoneNumberFieldSkin;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * <p>A control for entering phone numbers. By default, the phone numbers are expressed in international format, including
 * the country calling code and delivered by the {@link #rawPhoneNumberProperty() phone number} property.</p>
 *
 * <p>The control supports a list of {@link #getAvailableCountryCodes() available country codes} and works based on
 * the {@link CountryCallingCode CountryCallingCode} interface. This interface allows customizing the country codes and their
 * respective area codes, in case the default values are not sufficient or the numbers change.
 * </p>
 */
public class PhoneNumberField extends Control {

    /**
     * Pseudo class used to visualize the error state of the control.
     */
    public static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    /**
     * Default style class for css styling.
     */
    public static final String DEFAULT_STYLE_CLASS = "phone-number-field";

    private final CountryCallingCodeResolver resolver;
    private final PhoneNumberFormatter formatter;
    private final TextField textField;
    private final PhoneNumberUtil phoneNumberUtil;

    /**
     * Builds a new phone number field with the default settings. The available country calling codes is taken from
     * {@link CountryCallingCode.Defaults Defaults}.
     */
    public PhoneNumberField() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getAvailableCountryCodes().setAll(CountryCallingCode.Defaults.values());

        phoneNumberUtil = PhoneNumberUtil.getInstance();
        textField = new TextField();
        resolver = new CountryCallingCodeResolver();
        formatter = new PhoneNumberFormatter(textField);

        rawPhoneNumberProperty().addListener((obs, oldV, newV) -> Platform.runLater(() -> formatter.setFormattedNationalNumber(getRawPhoneNumber())));
        validProperty().addListener((obs, oldV, newV) -> pseudoClassStateChanged(ERROR_PSEUDO_CLASS, !newV));
    }

    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(PhoneNumberField.class.getResource("phone-number-field.css")).toExternalForm();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PhoneNumberFieldSkin(this, textField);
    }

    // VALUES
    private final StringProperty rawPhoneNumber = new SimpleStringProperty(this, "rawPhoneNumber") {
        private boolean selfUpdate;
        @Override
        public void set(String newRawPhoneNumber) {
            if (selfUpdate) {
                return;
            }

            try {
                selfUpdate = true;

                // Set the value first, so that the binding will be triggered
                super.set(newRawPhoneNumber);

                // Resolve all dependencies out of the raw phone number
                CountryCallingCode code = resolver.call(newRawPhoneNumber);

                if (code != null) {
                    setCountryCallingCode(code);
                    formatter.setFormattedNationalNumber(newRawPhoneNumber);

                    try {
                        Phonenumber.PhoneNumber number = phoneNumberUtil.parse(getRawPhoneNumber(), code.iso2Code());
                        setValid(phoneNumberUtil.isValidNumber(number));
                        setE164PhoneNumber(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
                        setNationalPhoneNumber(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                    } catch (Exception e) {
                        setValid(true);
                        setE164PhoneNumber(null);
                        setNationalPhoneNumber(null);
                    }
                } else {
                    setCountryCallingCode(null);
                    formatter.setFormattedNationalNumber(null);
                    setValid(true);
                    setE164PhoneNumber(null);
                    setNationalPhoneNumber(null);
                }

            } finally {
                selfUpdate = false;
            }
        }
    };

    /**
     * @return The raw phone number corresponding exactly to what the user typed in, including the (+) sign appended at the
     * beginning.  This value can be a valid E164 formatted number.
     */
    public final StringProperty rawPhoneNumberProperty() {
        return rawPhoneNumber;
    }

    public final String getRawPhoneNumber() {
        return rawPhoneNumberProperty().get();
    }

    public final void setRawPhoneNumber(String rawPhoneNumber) {
        rawPhoneNumberProperty().set(rawPhoneNumber);
    }

    private final ObjectProperty<CountryCallingCode> countryCallingCode = new SimpleObjectProperty<>(this, "countryCallingCode") {
        private boolean selfUpdate;
        @Override
        public void set(CountryCallingCode newCountryCallingCode) {
            if (selfUpdate) {
                return;
            }

            try {
                selfUpdate = true;

                // Set the value first, so that the binding will be triggered
                super.set(newCountryCallingCode);

                setRawPhoneNumber(newCountryCallingCode == null ? null : newCountryCallingCode.phonePrefix());

            } finally {
                selfUpdate = false;
            }
        }
    };

    /**
     * @return The selected country calling code.  Use this property if you want to define a default (pre-selected) country.
     * It can also be used in conjunction with {@link #disableCountryCodeProperty() disableCountryCode} to avoid changing the country part.
     */
    public final ObjectProperty<CountryCallingCode> countryCallingCodeProperty() {
        return countryCallingCode;
    }

    public final CountryCallingCode getCountryCallingCode() {
        return countryCallingCodeProperty().get();
    }

    private void setCountryCallingCode(CountryCallingCode countryCallingCode) {
        countryCallingCodeProperty().set(countryCallingCode);
    }

    private final ReadOnlyStringWrapper nationalPhoneNumber = new ReadOnlyStringWrapper(this, "nationalPhoneNumber");

    public final ReadOnlyStringProperty nationalPhoneNumberProperty() {
        return nationalPhoneNumber.getReadOnlyProperty();
    }

    public final String getNationalPhoneNumber() {
        return nationalPhoneNumber.get();
    }

    private void setNationalPhoneNumber(String nationalPhoneNumber) {
        this.nationalPhoneNumber.set(nationalPhoneNumber);
    }

    private final ReadOnlyStringWrapper e164PhoneNumber = new ReadOnlyStringWrapper(this, "e164PhoneNumber");

    public final ReadOnlyStringProperty e164PhoneNumberProperty() {
        return e164PhoneNumber.getReadOnlyProperty();
    }

    public final String getE164PhoneNumber() {
        return e164PhoneNumber.get();
    }

    private void setE164PhoneNumber(String e164PhoneNumber) {
        this.e164PhoneNumber.set(e164PhoneNumber);
    }

    // SETTINGS

    private final ObservableList<CountryCallingCode> availableCountryCodes = FXCollections.observableArrayList();

    /**
     * @return The list of available countries from which the user can select one and put it into the
     * {@link #countryCallingCodeProperty() countryCallingCode}.
     */
    public final ObservableList<CountryCallingCode> getAvailableCountryCodes() {
        return availableCountryCodes;
    }

    private final ObservableList<CountryCallingCode> preferredCountryCodes = FXCollections.observableArrayList();

    /**
     * @return The list of preferred countries that are shown first in the list of available countries.  If one country calling code
     * is added to this list, but is not present in the {@link #getAvailableCountryCodes()} then it will be ignored and not shown.
     */
    public final ObservableList<CountryCallingCode> getPreferredCountryCodes() {
        return preferredCountryCodes;
    }

    private final BooleanProperty disableCountryCode = new SimpleBooleanProperty(this, "disableCountryCode");

    /**
     * @return Flag to disable the country selector button. This will allow to specify a default country code and avoid changing it
     * in case it is wanted to be fixed.
     */
    public final BooleanProperty disableCountryCodeProperty() {
        return disableCountryCode;
    }

    public final boolean isDisableCountryCode() {
        return disableCountryCodeProperty().get();
    }

    public final void setDisableCountryCode(boolean disableCountryCode) {
        disableCountryCodeProperty().set(disableCountryCode);
    }

    private final ObjectProperty<Callback<CountryCallingCode, Node>> countryCodeViewFactory = new SimpleObjectProperty<>(this, "countryCodeViewFactory");

    /**
     * @return Factory that allows to replace the node used to graphically represent each country calling code.
     */
    public final ObjectProperty<Callback<CountryCallingCode, Node>> countryCodeViewFactoryProperty() {
        return countryCodeViewFactory;
    }

    public final Callback<CountryCallingCode, Node> getCountryCodeViewFactory() {
        return countryCodeViewFactoryProperty().get();
    }

    public final void setCountryCodeViewFactory(Callback<CountryCallingCode, Node> countryCodeViewFactory) {
        countryCodeViewFactoryProperty().set(countryCodeViewFactory);
    }

    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid") {
        @Override
        public void set(boolean newValid) {
            super.set(newValid);
            pseudoClassStateChanged(ERROR_PSEUDO_CLASS, !newValid);
        }
    };

    /**
     * @return Read only property that indicates whether the phone number is valid or not.
     */
    public final ReadOnlyBooleanWrapper validProperty() {
        return valid;
    }

    public final boolean isValid() {
        return valid.get();
    }

    private void setValid(boolean valid) {
        this.valid.set(valid);
    }

    /**
     * Represents a country calling code. The country calling code is used to identify the country and the area code.  This ones
     * should go according the ITU-T E.164 recommendation.
     * <a href="https://en.wikipedia.org/wiki/List_of_country_calling_codes">List_of_country_calling_codes</a>.
     */
    public interface CountryCallingCode {

        /**
         * @return The code assigned to the country.
         */
        int countryCode();

        /**
         * @return The Alpha-2 code of the country as described in the ISO-3166 international standard.
         */
        String iso2Code();

        /**
         * @return Designated area codes within the country.
         */
        int[] areaCodes();

        /**
         * @return The first area code if there is any in the country.
         */
        default Integer defaultAreaCode() {
            return areaCodes().length > 0 ? areaCodes()[0] : null;
        }

        /**
         * @return The concatenation of country code and {@link #defaultAreaCode() default area code} without the plus sign.
         */
        default String countryCodePrefix() {
            return "+" + countryCode();
        }

        /**
         * @return The concatenation of country code and {@link #defaultAreaCode() default area code} without the plus sign.
         */
        default String phonePrefix() {
            return countryCodePrefix() + Optional.ofNullable(defaultAreaCode()).map(Object::toString).orElse("");
        }

        /**
         * Default country calling codes offered by the control.
         */
        enum Defaults implements CountryCallingCode {

            AFGHANISTAN(93, "AF"),
            ALAND_ISLANDS(358, "AX", 18),
            ALBANIA(355, "AL"),
            ALGERIA(213, "DZ"),
            AMERICAN_SAMOA(1, "AS", 684),
            ANDORRA(376, "AD"),
            ANGOLA(244, "AO"),
            ANGUILLA(1, "AI", 264),
            ANTIGUA_AND_BARBUDA(1, "AG", 268),
            ARGENTINA(54, "AR"),
            ARMENIA(374, "AM"),
            ARUBA(297, "AW"),
            AUSTRALIA(61, "AU"),
            AUSTRALIA_ANTARCTIC_TERRITORIES(672, "AQ", 1),
            AUSTRIA(43, "AT"),
            AZERBAIJAN(994, "AZ"),
            BAHAMAS(1, "BS", 242),
            BAHRAIN(973, "BH"),
            BANGLADESH(880, "BD"),
            BARBADOS(1, "BB", 246),
            BELARUS(375, "BY"),
            BELGIUM(32, "BE"),
            BELIZE(501, "BZ"),
            BENIN(229, "BJ"),
            BERMUDA(1, "BM", 441),
            BHUTAN(975, "BT"),
            BOLIVIA(591, "BO"),
            BONAIRE(599, "BQ", 7),
            BOSNIA_AND_HERZEGOVINA(387, "BA"),
            BOTSWANA(267, "BW"),
            BRAZIL(55, "BR"),
            BRITISH_INDIAN_OCEAN_TERRITORY(246, "IO"),
            BRITISH_VIRGIN_ISLANDS(1, "VG", 284),
            BRUNEI(673, "BN"),
            BULGARIA(359, "BG"),
            BURKINA_FASO(226, "BF"),
            BURUNDI(257, "BI"),
            CAMBODIA(855, "KH"),
            CAMEROON(237, "CM"),
            CANADA(1, "CA"),
            CAPE_VERDE(238, "CV"),
            CAYMAN_ISLANDS(1, "KY", 345),
            CENTRAL_AFRICAN_REPUBLIC(236, "CF"),
            CHAD(235, "TD"),
            CHILE(56, "CL"),
            CHINA(86, "CN"),
            CHRISTMAS_ISLAND(61, "CX", 89164),
            COCOS_ISLANDS(61, "CC", 89162),
            COLOMBIA(57, "CO"),
            COMOROS(269, "KM"),
            CONGO(242, "CG"),
            COOK_ISLANDS(682, "CK"),
            COSTA_RICA(506, "CR"),
            CROATIA(385, "HR"),
            CUBA(53, "CU"),
            CURACAO(599, "CW", 9),
            CYPRUS(357, "CY"),
            CZECH_REPUBLIC(420, "CZ"),
            DEMOCRATIC_REPUBLIC_OF_THE_CONGO(243, "CD"),
            DENMARK(45, "DK"),
            DJIBOUTI(253, "DJ"),
            DOMINICA(1, "DM", 767),
            DOMINICAN_REPUBLIC(1, "DO", 809, 829, 849),
            EAST_TIMOR(670, "TL"),
            ECUADOR(593, "EC"),
            EGYPT(20, "EG"),
            EL_SALVADOR(503, "SV"),
            EQUATORIAL_GUINEA(240, "GQ"),
            ERITREA(291, "ER"),
            ESTONIA(372, "EE"),
            ETHIOPIA(251, "ET"),
            FALKLAND_ISLANDS(500, "FK"),
            FAROE_ISLANDS(298, "FO"),
            FIJI(679, "FJ"),
            FINLAND(358, "FI"),
            FRANCE(33, "FR"),
            FRENCH_GUIANA(594, "GF"),
            FRENCH_POLYNESIA(689, "PF"),
            GABON(241, "GA"),
            GAMBIA(220, "GM"),
            GEORGIA(995, "GE"),
            GERMANY(49, "DE"),
            GHANA(233, "GH"),
            GIBRALTAR(350, "GI"),
            GREECE(30, "GR"),
            GREENLAND(299, "GL"),
            GRENADA(1, "GD", 473),
            GUADELOUPE(590, "GP"),
            GUAM(1, "GU", 671),
            GUATEMALA(502, "GT"),
            GUERNSEY(44, "GG", 1481, 7781, 7839, 7911),
            GUINEA(224, "GN"),
            GUINEA_BISSAU(245, "GW"),
            GUYANA(592, "GY"),
            HAITI(509, "HT"),
            HONDURAS(504, "HN"),
            HONG_KONG(852, "HK"),
            HUNGARY(36, "HU"),
            ICELAND(354, "IS"),
            INDIA(91, "IN"),
            INDONESIA(62, "ID"),
            IRAN(98, "IR"),
            IRAQ(964, "IQ"),
            IRELAND(353, "IE"),
            ISLE_OF_MAN(44, "IM", 1624, 7524, 7624, 7924),
            ISRAEL(972, "IL"),
            ITALY(39, "IT"),
            IVORY_COAST(225, "CI"),
            JAMAICA(1, "JM", 658, 876),
            JAN_MAYEN(47, "SJ", 79),
            JAPAN(81, "JP"),
            JERSEY(44, "JE", 1534),
            JORDAN(962, "JO"),
            KAZAKHSTAN(7, "KZ", 6, 7),
            KENYA(254, "KE"),
            KIRIBATI(686, "KI"),
            KOREA_NORTH(850, "KP"),
            KOREA_SOUTH(82, "KR"),
            KOSOVO(383, "XK"),
            KUWAIT(965, "KW"),
            KYRGYZSTAN(996, "KG"),
            LAOS(856, "LA"),
            LATVIA(371, "LV"),
            LEBANON(961, "LB"),
            LESOTHO(266, "LS"),
            LIBERIA(231, "LR"),
            LIBYA(218, "LY"),
            LIECHTENSTEIN(423, "LI"),
            LITHUANIA(370, "LT"),
            LUXEMBOURG(352, "LU"),
            MACAU(853, "MO"),
            MACEDONIA(389, "MK"),
            MADAGASCAR(261, "MG"),
            MALAWI(265, "MW"),
            MALAYSIA(60, "MY"),
            MALDIVES(960, "MV"),
            MALI(223, "ML"),
            MALTA(356, "MT"),
            MARSHALL_ISLANDS(692, "MH"),
            MARTINIQUE(596, "MQ"),
            MAURITANIA(222, "MR"),
            MAURITIUS(230, "MU"),
            MAYOTTE(262, "YT", 269, 639),
            MEXICO(52, "MX"),
            MICRONESIA(691, "FM"),
            MOLDOVA(373, "MD"),
            MONACO(377, "MC"),
            MONGOLIA(976, "MN"),
            MONTENEGRO(382, "ME"),
            MONTSERRAT(1, "MS", 664),
            MOROCCO(212, "MA"),
            MOZAMBIQUE(258, "MZ"),
            MYANMAR(95, "MM"),
            NAMIBIA(264, "NA"),
            NAURU(674, "NR"),
            NEPAL(977, "NP"),
            NETHERLANDS(31, "NL"),
            NEW_CALEDONIA(687, "NC"),
            NEW_ZEALAND(64, "NZ"),
            NICARAGUA(505, "NI"),
            NIGER(227, "NE"),
            NIGERIA(234, "NG"),
            NIUE(683, "NU"),
            NORFOLK_ISLAND(672, "NF", 3),
            NORTHERN_MARIANA_ISLANDS(1, "MP", 670),
            NORWAY(47, "NO"),
            OMAN(968, "OM"),
            PAKISTAN(92, "PK"),
            PALAU(680, "PW"),
            PALESTINE(970, "PS"),
            PANAMA(507, "PA"),
            PAPUA_NEW_GUINEA(675, "PG"),
            PARAGUAY(595, "PY"),
            PERU(51, "PE"),
            PHILIPPINES(63, "PH"),
            POLAND(48, "PL"),
            PORTUGAL(351, "PT"),
            PUERTO_RICO(1, "PR", 787, 930),
            QATAR(974, "QA"),
            REUNION(262, "RE"),
            ROMANIA(40, "RO"),
            RUSSIA(7, "RU"),
            RWANDA(250, "RW"),
            SAINT_HELENA(290, "SH"),
            SAINT_KITTS_AND_NEVIS(1, "KN", 869),
            SAINT_LUCIA(1, "LC", 758),
            SAINT_PIERRE_AND_MIQUELON(508, "PM"),
            SAINT_VINCENT_AND_THE_GRENADINES(1, "VC", 784),
            SAMOA(685, "WS"),
            SAN_MARINO(378, "SM"),
            SAO_TOME_AND_PRINCIPE(239, "ST"),
            SAUDI_ARABIA(966, "SA"),
            SENEGAL(221, "SN"),
            SERBIA(381, "RS"),
            SEYCHELLES(248, "SC"),
            SIERRA_LEONE(232, "SL"),
            SINGAPORE(65, "SG"),
            SLOVAKIA(421, "SK"),
            SLOVENIA(386, "SI"),
            SOLOMON_ISLANDS(677, "SB"),
            SOMALIA(252, "SO"),
            SOUTH_AFRICA(27, "ZA"),
            SOUTH_SUDAN(211, "SS"),
            SPAIN(34, "ES"),
            SRI_LANKA(94, "LK"),
            SUDAN(249, "SD"),
            SURINAME(597, "SR"),
            SVALBARD_AND_JAN_MAYEN(47, "SJ"),
            SWAZILAND(268, "SZ"),
            SWEDEN(46, "SE"),
            SWITZERLAND(41, "CH"),
            SYRIA(963, "SY"),
            TAIWAN(886, "TW"),
            TAJIKISTAN(992, "TJ"),
            TANZANIA(255, "TZ"),
            THAILAND(66, "TH"),
            TOGO(228, "TG"),
            TOKELAU(690, "TK"),
            TONGA(676, "TO"),
            TRINIDAD_AND_TOBAGO(1, "TT", 868),
            TUNISIA(216, "TN"),
            TURKEY(90, "TR"),
            TURKMENISTAN(993, "TM"),
            TURKS_AND_CAICOS_ISLANDS(1, "TC", 649),
            TUVALU(688, "TV"),
            UGANDA(256, "UG"),
            UKRAINE(380, "UA"),
            UNITED_ARAB_EMIRATES(971, "AE"),
            UNITED_KINGDOM(44, "GB"),
            UNITED_STATES(1, "US"),
            URUGUAY(598, "UY"),
            UZBEKISTAN(998, "UZ"),
            VANUATU(678, "VU"),
            VATICAN_CITY(379, "VA"),
            VENEZUELA(58, "VE"),
            VIETNAM(84, "VN"),
            VIRGIN_ISLANDS(1, "VI", 340),
            WALLIS_AND_FUTUNA(681, "WF"),
            WESTERN_SAHARA(212, "EH"),
            YEMEN(967, "YE"),
            ZAMBIA(260, "ZM"),
            ZANZIBAR(255, "TZ"),
            ZIMBABWE(263, "ZW")
            ;

            private final int countryCode;
            private final String iso2Code;
            private final int[] areaCodes;

            Defaults(int countryCode, String iso2Code, int... areaCodes) {
                this.countryCode = countryCode;
                this.iso2Code = iso2Code;
                this.areaCodes = Optional.ofNullable(areaCodes).orElse(new int[0]);
            }

            @Override
            public int countryCode() {
                return countryCode;
            }

            @Override
            public int[] areaCodes() {
                return areaCodes;
            }

            @Override
            public String iso2Code() {
                return iso2Code;
            }

        }

    }

    /**
     * For internal use only.
     */
    private final class PhoneNumberFormatter implements UnaryOperator<TextFormatter.Change> {

        private PhoneNumberFormatter(TextField textField) {
            textField.setTextFormatter(new TextFormatter<>(this));
            textField.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.BACK_SPACE
                    && (textField.getText() == null || textField.getText().isEmpty())
                    && getCountryCallingCode() != null) {

                    // Clear up the country code if the user deletes the entire text
                    setRawPhoneNumber(null);
                    e.consume();
                }
            });
        }

        private boolean selfUpdate;

        @Override
        public TextFormatter.Change apply(TextFormatter.Change change) {
            if (selfUpdate) {
                return change;
            }

            try {
                selfUpdate = true;
                CountryCallingCode code = getCountryCallingCode();

                if (change.isAdded()) {
                    String text = change.getText();

                    if (code == null && text.startsWith("+")) {
                        text = text.substring(1);
                    }

                    if (!text.isEmpty() && !text.matches("[0-9]+")) {
                        return null;
                    }

                    if (code == null && !change.getControlNewText().startsWith("+")) {
                        change.setText("+" + change.getText());
                        change.setCaretPosition(change.getCaretPosition() + 1);
                        change.setAnchor(change.getAnchor() + 1);
                    }
                }

                if (change.isContentChange()) {
                    if (code == null) {
                        resolveCountryCode(change);
                    } else {
                        String nationalNumber = undoFormat(change.getControlNewText());
                        String newPhoneNumber = code.countryCodePrefix() + nationalNumber;
                        setRawPhoneNumber(newPhoneNumber);
                    }
                }

            } finally {
                selfUpdate = false;
            }

            return change;
        }

        private void resolveCountryCode(TextFormatter.Change change) {
            CountryCallingCode code = resolver.call(change.getControlNewText());
            if (code != null) {
                setCountryCallingCode(code);
                textField.setText(Optional.ofNullable(code.defaultAreaCode()).map(String::valueOf).orElse(""));
                change.setText("");
                change.setCaretPosition(0);
                change.setAnchor(0);
                change.setRange(0, 0);
            }
        }

        private String doFormat(String newRawPhoneNumber) {
            if (newRawPhoneNumber == null || newRawPhoneNumber.isEmpty() || getCountryCallingCode() == null) {
                return "";
            }

            CountryCallingCode code = getCountryCallingCode();
            AsYouTypeFormatter formatter = phoneNumberUtil.getAsYouTypeFormatter(code.iso2Code());
            String formattedNumber = "";

            for (char c : newRawPhoneNumber.toCharArray()) {
                formattedNumber = formatter.inputDigit(c);
            }

            return formattedNumber.substring(code.countryCodePrefix().length()).trim();
        }

        private String undoFormat(String formattedLocalPhoneNumber) {
            StringBuilder phoneNumber = new StringBuilder();

            if (formattedLocalPhoneNumber != null && !formattedLocalPhoneNumber.isEmpty()) {
                for (char  c: formattedLocalPhoneNumber.toCharArray()) {
                    if (Character.isDigit(c)) {
                        phoneNumber.append(c);
                    }
                }
            }

            return phoneNumber.toString();
        }

        private void setFormattedNationalNumber(String newRawPhoneNumber) {
            if (selfUpdate) {
                return; // Ignore when I'm the one who initiated the update
            }

            try {
                selfUpdate = true;
                String formattedPhoneNumber = doFormat(newRawPhoneNumber);
                textField.setText(formattedPhoneNumber);
                textField.positionCaret(formattedPhoneNumber.length());
            } finally {
                selfUpdate = false;
            }
        }

    }

    /**
     * For internal use only.
     */
    private final class CountryCallingCodeResolver implements Callback<String, CountryCallingCode> {

        @Override
        public CountryCallingCode call(String phoneNumber) {
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                return null;
            }

            if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.substring(1);
                if (phoneNumber.isEmpty()) {
                    return null;
                }
            }

            TreeMap<Integer, List<CountryCallingCode>> scores = new TreeMap<>();

            for (CountryCallingCode code : getAvailableCountryCodes()) {
                int score = calculateScore(code, phoneNumber);
                if (score > 0) {
                    scores.computeIfAbsent(score, s -> new ArrayList<>()).add(code);
                }
            }

            Map.Entry<Integer, List<CountryCallingCode>> highestScore = scores.lastEntry();
            if (highestScore == null) {
                return null;
            }

            return inferBestMatch(highestScore.getValue());
        }

        private int calculateScore(CountryCallingCode code, String phoneNumber) {
            String countryPrefix = String.valueOf(code.countryCode());

            if (code.areaCodes().length == 0) {
                if (phoneNumber.startsWith(countryPrefix)) {
                    return 1;
                }
            } else {
                for (int areaCode : code.areaCodes()) {
                    String areaCodePrefix = countryPrefix + areaCode;
                    if (phoneNumber.startsWith(areaCodePrefix)) {
                        return 2;
                    }
                }
            }

            return 0;
        }

        private CountryCallingCode inferBestMatch(List<CountryCallingCode> matchingCodes) {
            CountryCallingCode code = null;
            if (matchingCodes.size() > 1) {
                // Here pick the country code that is preferred
                for (CountryCallingCode c : matchingCodes) {
                    if (getPreferredCountryCodes().contains(c)) {
                        code = c;
                        break;
                    }
                }

                if (code == null) {
                    code = matchingCodes.get(matchingCodes.size() - 1);
                }
            } else {
                code = matchingCodes.get(0);
            }
            return code;
        }

    }

}
