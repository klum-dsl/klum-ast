package ideacceptance

import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Field

@DSL
class BaseItem {
    String inherited

    @Builder.Method
    String inheritedOperation(String value) {
        inherited = value
    }
}

@DSL
class Item extends BaseItem {
    String title
    int count
    String mode
    String precise

    @Builder.Method
    String mode(Integer value) {
        mode = "number:$value"
    }

    @Builder.Method
    void overloaded(String value) {
        title = value
    }

    @Builder.Method
    void overloaded(Integer value) {
        count = value
    }
}

@DSL
class Catalog {
    String name
    Item primary

    @Field(members = 'listed')
    List<Item> listedItems
}
