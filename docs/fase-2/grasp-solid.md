# Aplicación preliminar de GRASP y SOLID

**Estado:** propuesta para guiar el diseño. Esta tabla no demuestra que los principios ya estén implementados; la evidencia final deberá enlazarse con el código real.

## 1. Patrones GRASP

| Patrón | Clase o componente propuesto | Justificación |
|---|---|---|
| Information Expert | BeerProduct | Conoce la capacidad por canasta, las canastas disponibles y las unidades sueltas; puede calcular las unidades equivalentes. |
| Information Expert | Sale | Conoce sus líneas y pagos; puede calcular el total, el importe abonado y el saldo. |
| Creator | Sale | Es responsable de mantener sus líneas y pagos asociados, por lo que puede centralizar su creación o incorporación. |
| Controller | SaleService | Recibe la solicitud de registrar una venta y coordina validaciones, inventario, líneas y pagos, sin presentar el menú. |
| Low Coupling | UI → Service → Persistence | La interfaz no accede directamente a repositorios y la lógica de negocio no depende del formato de interacción. |
| High Cohesion | ProductService, SaleService, AccountReceivableService, PurchaseOrderService | Cada servicio se concentra en un grupo coherente de casos de uso. |
| Polymorphism | SalePresentation y sus implementaciones | El cálculo de unidades e importe se delega a la presentación seleccionada: unidad, media canasta o canasta completa. |
| Pure Fabrication | Repositorios/DAO | Las clases de persistencia encapsulan el almacenamiento sin agregar esa responsabilidad a las entidades del negocio. |
| Indirection | Servicios entre UI y repositorios | La UI no necesita conocer cómo se almacenan o coordinan los datos. |
| Protected Variations | SalePresentation y contratos de repositorio | Las reglas variables de presentación y almacenamiento quedan detrás de interfaces para poder extenderlas o sustituirlas. |

## 2. Principios SOLID

| Principio | Aplicación propuesta | Evidencia que deberá comprobarse |
|---|---|---|
| SRP — Responsabilidad única | ConsoleMenu presenta opciones; SaleService coordina la venta; BeerProduct administra las reglas propias del producto; los repositorios almacenan y recuperan datos. | Cada clase debe tener una responsabilidad principal y no mezclar presentación, negocio y persistencia. |
| OCP — Abierto/cerrado | SalePresentation permite añadir otra presentación con una nueva implementación sin rehacer el flujo general de SaleService. | Debe poder añadirse un formato de venta mediante una implementación nueva, manteniendo el contrato común. |
| LSP — Sustitución de Liskov | Customer y Seller pueden utilizarse donde se requiere la información común de Person. | Las subclases mantienen el contrato de Person y no invalidan las operaciones comunes. |
| ISP — Segregación de interfaces | Mantener contratos de repositorio específicos por módulo en vez de una única interfaz enorme. | ProductRepository y SaleRepository exponen solo las operaciones que corresponden a su responsabilidad. |
| DIP — Inversión de dependencias | Los servicios deberán depender de abstracciones de repositorio y no de una implementación concreta de almacenamiento. | El servicio puede probarse con una implementación alternativa de repositorio. |

## 3. Reglas para aplicar estos principios durante el desarrollo

1. No añadir una interfaz o una clase abstracta solo para aparentar aplicar un principio. Debe resolver una variación o responsabilidad real.
2. Antes de modificar una clase, identificar qué responsabilidad tiene y qué otras clases dependen de ella.
3. Mantener los cálculos de precios y cantidades fuera del menú de consola.
4. Los métodos de inventario deben validar unidades y proteger la regla de no permitir stock negativo.
5. La interfaz JavaFX futura debe reutilizar los servicios existentes y no duplicar reglas de negocio.
6. Cada integrante debe documentar los principios que realmente se observen en su código. No se declarará un principio cumplido si no se encuentra evidencia.
