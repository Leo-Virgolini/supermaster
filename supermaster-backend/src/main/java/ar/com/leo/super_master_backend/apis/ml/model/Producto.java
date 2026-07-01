package ar.com.leo.super_master_backend.apis.ml.model;

import ar.com.leo.super_master_backend.apis.ml.service.MlUnidades;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Producto {

    @JsonProperty("id")
    public String id;

    @JsonProperty("site_id")
    public String siteId;

    @JsonProperty("title")
    public String title;

    @JsonProperty("family_name")
    public String familyName;

    @JsonProperty("family_id")
    public Long familyId;

    @JsonProperty("seller_id")
    public Long sellerId;

    @JsonProperty("category_id")
    public String categoryId;

    @JsonProperty("user_product_id")
    public String userProductId;

    @JsonProperty("official_store_id")
    public Object officialStoreId;

    @JsonProperty("price")
    public Double price;

    @JsonProperty("base_price")
    public Double basePrice;

    @JsonProperty("original_price")
    public Object originalPrice;

    @JsonProperty("inventory_id")
    public Object inventoryId;

    @JsonProperty("currency_id")
    public String currencyId;

    @JsonProperty("initial_quantity")
    public Integer initialQuantity;

    @JsonProperty("available_quantity")
    public Integer availableQuantity;

    @JsonProperty("sold_quantity")
    public Integer soldQuantity;

    @JsonProperty("sale_terms")
    public List<SaleTerm> saleTerms;

    @JsonProperty("buying_mode")
    public String buyingMode;

    @JsonProperty("listing_type_id")
    public String listingTypeId;

    @JsonProperty("start_time")
    public String startTime;

    @JsonProperty("stop_time")
    public String stopTime;

    @JsonProperty("end_time")
    public String endTime;

    @JsonProperty("expiration_time")
    public String expirationTime;

    @JsonProperty("condition")
    public String condition;

    @JsonProperty("permalink")
    public String permalink;

    @JsonProperty("thumbnail_id")
    public String thumbnailId;

    @JsonProperty("thumbnail")
    public String thumbnail;

    @JsonProperty("pictures")
    public List<Picture> pictures;

    @JsonProperty("video_id")
    public Object videoId;

    @JsonProperty("descriptions")
    public List<Object> descriptions;

    @JsonProperty("accepts_mercadopago")
    public Boolean acceptsMercadoPago;

    @JsonProperty("non_mercado_pago_payment_methods")
    public List<Object> nonMercadoPagoPaymentMethods;

    @JsonProperty("shipping")
    public Shipping shipping;

    @JsonProperty("international_delivery_mode")
    public String internationalDeliveryMode;

    @JsonProperty("seller_address")
    public SellerAddress sellerAddress;

    @JsonProperty("seller_contact")
    public Object sellerContact;

    @JsonProperty("location")
    public Object location;

    @JsonProperty("geolocation")
    public Geolocation geolocation;

    @JsonProperty("coverage_areas")
    public List<Object> coverageAreas;

    @JsonProperty("attributes")
    public List<Attribute> attributes;

    @JsonProperty("warnings")
    public List<Object> warnings;

    @JsonProperty("listing_source")
    public String listingSource;

    @JsonProperty("variations")
    public List<Object> variations;

    @JsonProperty("status")
    public String status;

    @JsonProperty("sub_status")
    public List<Object> subStatus;

    @JsonProperty("tags")
    public List<String> tags;

    @JsonProperty("warranty")
    public String warranty;

    @JsonProperty("catalog_product_id")
    public Object catalogProductId;

    @JsonProperty("domain_id")
    public String domainId;

    @JsonProperty("seller_custom_field")
    public Object sellerCustomField;

    @JsonProperty("parent_item_id")
    public Object parentItemId;

    @JsonProperty("differential_pricing")
    public Object differentialPricing;

    @JsonProperty("deal_ids")
    public List<Object> dealIds;

    @JsonProperty("automatic_relist")
    public Boolean automaticRelist;

    @JsonProperty("date_created")
    public String dateCreated;

    @JsonProperty("last_updated")
    public String lastUpdated;

    @JsonProperty("health")
    public Double health;

    @JsonProperty("catalog_listing")
    public Boolean catalogListing;

    @JsonProperty("item_relations")
    public List<Object> itemRelations;

    @JsonProperty("channels")
    public List<String> channels;

    @JsonIgnore
    public String getDimensions() {
        // 1️⃣ Revisar shipping.dimensions
        if (this.shipping != null && this.shipping.dimensions != null
                && !this.shipping.dimensions.toString().isEmpty()) {
            return this.shipping.dimensions.toString();
        }

        // 2️⃣ Revisar atributos (usando struct para obtener número y unidad correctamente)
        Double lengthCm = null, widthCm = null, heightCm = null, weightG = null;
        if (this.attributes != null) {
            for (Attribute attr : this.attributes) {
                if (attr.id == null || attr.values == null) continue;
                Attribute.Value.Struct struct = null;
                for (Attribute.Value v : attr.values) {
                    if (v.struct != null && v.struct.number != null) {
                        struct = v.struct;
                        break;
                    }
                }
                if (struct == null) continue;

                switch (attr.id) {
                    case "SELLER_PACKAGE_LENGTH":
                        lengthCm = convertirACm(struct.number, struct.unit);
                        break;
                    case "SELLER_PACKAGE_WIDTH":
                        widthCm = convertirACm(struct.number, struct.unit);
                        break;
                    case "SELLER_PACKAGE_HEIGHT":
                        heightCm = convertirACm(struct.number, struct.unit);
                        break;
                    case "SELLER_PACKAGE_WEIGHT":
                        weightG = convertirAGramos(struct.number, struct.unit);
                        break;
                }
            }

            if (lengthCm != null && widthCm != null && heightCm != null && weightG != null) {
                // Formato API: altura x ancho x largo, peso (en cm y gramos)
                return String.format("%.0fx%.0fx%.0f,%.0f", heightCm, widthCm, lengthCm, weightG);
            }
        }

        // 3️⃣ Si no se encuentra
        return null;
    }

    private static Double convertirACm(double number, String unit) {
        return MlUnidades.aCm(number, unit);
    }

    private static Double convertirAGramos(double number, String unit) {
        return MlUnidades.aGramos(number, unit);
    }

    // ----- Clases internas -----
    public static class SaleTerm {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("value_id")
        public Object valueId;

        @JsonProperty("value_name")
        public String valueName;

        @JsonProperty("value_struct")
        public Object valueStruct;

        @JsonProperty("values")
        public List<Value> values;

        @JsonProperty("value_type")
        public String valueType;

        public static class Value {
            @JsonProperty("id")
            public Object id;

            @JsonProperty("name")
            public String name;

            @JsonProperty("struct")
            public Object struct;
        }
    }

    public static class Picture {
        @JsonProperty("id")
        public String id;

        @JsonProperty("url")
        public String url;

        @JsonProperty("secure_url")
        public String secureUrl;

        @JsonProperty("size")
        public String size;

        @JsonProperty("max_size")
        public String maxSize;

        @JsonProperty("quality")
        public String quality;
    }

    public static class Shipping {
        @JsonProperty("mode")
        public String mode;

        @JsonProperty("methods")
        public List<Object> methods;

        @JsonProperty("tags")
        public List<String> tags;

        @JsonProperty("dimensions")
        public Object dimensions;

        @JsonProperty("local_pick_up")
        public Boolean localPickUp;

        @JsonProperty("free_shipping")
        public Boolean freeShipping;

        @JsonProperty("logistic_type")
        public String logisticType;

        @JsonProperty("store_pick_up")
        public Boolean storePickUp;
    }

    public static class SellerAddress {
        @JsonProperty("address_line")
        public String addressLine;

        @JsonProperty("zip_code")
        public String zipCode;

        @JsonProperty("city")
        public IdName city;

        @JsonProperty("state")
        public IdName state;

        @JsonProperty("country")
        public IdName country;

        @JsonProperty("search_location")
        public SearchLocation searchLocation;

        @JsonProperty("latitude")
        public Double latitude;

        @JsonProperty("longitude")
        public Double longitude;

        @JsonProperty("id")
        public long id;

        public static class IdName {
            @JsonProperty("id")
            public String id;

            @JsonProperty("name")
            public String name;
        }

        public static class SearchLocation {
            @JsonProperty("city")
            public IdName city;

            @JsonProperty("state")
            public IdName state;
        }
    }

    public static class Geolocation {
        @JsonProperty("latitude")
        public Double latitude;

        @JsonProperty("longitude")
        public Double longitude;
    }

    public static class Attribute {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("value_id")
        public Object valueId;

        @JsonProperty("value_name")
        public String valueName;

        @JsonProperty("values")
        public List<Value> values;

        @JsonProperty("value_type")
        public String valueType;

        public static class Value {
            @JsonProperty("id")
            public Object id;

            @JsonProperty("name")
            public String name;

            @JsonProperty("struct")
            public Struct struct;

            public static class Struct {
                @JsonProperty("number")
                public Double number;

                @JsonProperty("unit")
                public String unit;
            }
        }
    }

    @Override
    public String toString() {
        return "Producto{" +
                "id='" + id + '\'' +
                ", siteId='" + siteId + '\'' +
                ", title='" + title + '\'' +
                ", familyName='" + familyName + '\'' +
                ", familyId=" + familyId +
                ", sellerId=" + sellerId +
                ", categoryId='" + categoryId + '\'' +
                ", userProductId='" + userProductId + '\'' +
                ", officialStoreId=" + officialStoreId +
                ", price=" + price +
                ", basePrice=" + basePrice +
                ", originalPrice=" + originalPrice +
                ", inventoryId=" + inventoryId +
                ", currencyId='" + currencyId + '\'' +
                ", initialQuantity=" + initialQuantity +
                ", availableQuantity=" + availableQuantity +
                ", soldQuantity=" + soldQuantity +
                ", saleTerms=" + saleTerms +
                ", buyingMode='" + buyingMode + '\'' +
                ", listingTypeId='" + listingTypeId + '\'' +
                ", startTime='" + startTime + '\'' +
                ", stopTime='" + stopTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", expirationTime='" + expirationTime + '\'' +
                ", condition='" + condition + '\'' +
                ", permalink='" + permalink + '\'' +
                ", thumbnailId='" + thumbnailId + '\'' +
                ", thumbnail='" + thumbnail + '\'' +
                ", pictures=" + pictures +
                ", videoId=" + videoId +
                ", descriptions=" + descriptions +
                ", acceptsMercadoPago=" + acceptsMercadoPago +
                ", nonMercadoPagoPaymentMethods=" + nonMercadoPagoPaymentMethods +
                ", shipping=" + shipping +
                ", internationalDeliveryMode='" + internationalDeliveryMode + '\'' +
                ", sellerAddress=" + sellerAddress +
                ", sellerContact=" + sellerContact +
                ", location=" + location +
                ", geolocation=" + geolocation +
                ", coverageAreas=" + coverageAreas +
                ", attributes=" + attributes +
                ", warnings=" + warnings +
                ", listingSource='" + listingSource + '\'' +
                ", variations=" + variations +
                ", status='" + status + '\'' +
                ", subStatus=" + subStatus +
                ", tags=" + tags +
                ", warranty='" + warranty + '\'' +
                ", catalogProductId=" + catalogProductId +
                ", domainId='" + domainId + '\'' +
                ", sellerCustomField=" + sellerCustomField +
                ", parentItemId=" + parentItemId +
                ", differentialPricing=" + differentialPricing +
                ", dealIds=" + dealIds +
                ", automaticRelist=" + automaticRelist +
                ", dateCreated='" + dateCreated + '\'' +
                ", lastUpdated='" + lastUpdated + '\'' +
                ", health=" + health +
                ", catalogListing=" + catalogListing +
                ", itemRelations=" + itemRelations +
                ", channels=" + channels +
                '}';
    }
}


