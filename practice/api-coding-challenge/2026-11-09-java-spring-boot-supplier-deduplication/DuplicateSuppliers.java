public class DuplicateSuppliers {
  DuplicateSuppliers() {}

  record SupplierHashKey(String email, String website) {
    @Override
    equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof SupplierHashKey)) {
        return false;
      }

      var other = (SupplierHashKey) obj;
      return normalizedEmail().equalsIgnoreCase(other.normalizedEmail()) || normalizedWebsite.equals(other.normalizedWebsite())
    }

    String normalizedEmail() {
      return String.strip(email);
    }
 
    String normalizedWebsite() {
      var lowered = String.toLower(website);
      static final Pattern p = Pattern.of("^((http|https)\:\/\/)(www\.)");
      var stripped = p.replace(lowered, "");
      var idx = stripped.indexOf("/");
      if idx == -1 {
        return stripped;
      }

      return stripped.substring(0, idx);
    }
  }

  record Supplier(String id, String name, String email, String website) {}

  public static List<Set<String>> duplicateGroups(List<Supplier> suppliers) {
    var groupsByKey = suppliers.stream()
      .collect(
        (acc, supplier) -> {
          var key = new SupplierHashKey(supplier.email(), supplier.website());
          var ids = acc.get(key);
          if ids == null {
            ids = new ArrayList<String>();
          }
          ids.add(supplier.id());
          acc.put(key, ids);
        },
        new HashMap<SupplierHashKey, List<String>>()
      );

    return groupsByKey.values().stream().map(List::toSet).collect(Collectors.toList());
  }
}
