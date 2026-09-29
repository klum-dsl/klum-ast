package ideacceptance

import groovy.transform.CompileStatic

@CompileStatic
class SameProjectClient {
    static Catalog root() {
        Catalog.Create.With(name: 'root') {
            primary(title: 'single child')
            listed(title: 'collection child')
        }
    }

    static Item item() {
        Item.Create.With(
                inherited: 'inherited field',
                inheritedOperation: 'inherited method',
                setTitle: 'setter alias',
                mode: 7,
                precise: 'text',
                overloaded: 'safe String overload')
    }

    static Catalog fromMapVariable(Map<String, ?> values) {
        Catalog.Create.With(values)
    }
}
