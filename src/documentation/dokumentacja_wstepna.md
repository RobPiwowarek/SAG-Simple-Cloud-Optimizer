### Agent lokalny
Głównym zadaniem agenta lokalnego jest obserwowanie zużycia zasobów maszyn przez przypisanego do niego klienta oraz budowanie historii, która umożliwiłaby przewidywanie wymagań użytkownika w chwili t+N. Zbierane dane są przekształcane do postaci szeregu czasowego składającego się z wymagań na zasoby systemowe w chwilach t-1, t-2, ..., t-N.

Dane te są na bierząco aktualizowane, w celu poprawienia jakości modelu predykcyjnego służącego do przewidywania wymagań użytkownika. Celem tego typu podejścia jest uniknięcie sytuacji, w których klientowi zostałoby przypisanych zbyt wiele lub zbyt mało zasobów, biorąc pod uwagę także inne kwestie, takie jak liczba zapytań na jednostkę czasową, czas odpowiedzi itp. Wynik przewidywania jest kolejnie wysyłany to agenta globalnego, którego zadaniem jest zarządzanie zasobami i w razie potrzeby odebranie lub przydzielenie ich większej ilości.

Architektura oraz schemat przepływu informacji agenta lokalnego są przedstawione na poniższych zdjęciach.

<p align="center">
  <img src = "https://imgur.com/iCJQ5Rb.png"/>
   <figcaption>Architektura agenta lokalnego</figcaption>
</p>

<p align="center">
  <img src = "https://imgur.com/bl97feT.png"/>
   <figcaption>Schemat przepływu informacji w obrębie agenta lokalnego</figcaption>
</p>

W celu przewidywania wymagań klienta agent korzysta z jednego z modeli statystycznych służących do przewisywania wartości ciągłych. Aby był on w stanie działać w czasie rzeczywistym, musi być na tyle prosty w budowie, aby czas jego aktualizacji nie zakłócał pracy użytkownika. Modelami spełniającymi dane wymagania byłyby prawdopodobnie regresja liniowa, perceptron bądź las losowy. Dla przykładu, skupimy się na opisie tylko jednego z nich. Pomimo wszystko podczas implementacji przetestujemy każdy, wybierając ten, który spełnia wyżej opisane wymagania dając jednocześnie najlepszą jakośc przewidywań.

#### Model regresji liniowej
Zbiór danych historycznych będzie reprezentowany w postaci {yi, xi1, ..., xip}^n, gdzie zmienne xi1, ...xip będą kolejnymi wartościami szeregu czasowego stworzonego na podstawie historii zużycia danych przez użytkownika. Model regresji liniowej zakłada, że istnieje liniowa relacja pomiędzy zmienną zależną y a wektorem p x 1 regresorów xi.  Zależność ta jest modelowana przez uwzględnienie składnika losowego (błędu) Epsilon, który jest zmienną losową. Dokładniej, model ten jest postaci:

<p align="center">
  <img src = "https://imgur.com/xIFStFd.png"/>
</p>

dla i = 1, ..., n

W danym przypadku symbol T służy do reprezentowania transpozycji, więc xi ^ T * B to iloczyn skalarny xi oraz B.

Dla uproszczenia obliczeń, powyższe równanie można zapisać w sposób macierzowy y = XB + epsilon, gdzie:

<p align="center">
  <img src = "https://imgur.com/JOyUsdo.png"/>
</p>

Najczęściej wykorzystuje się do tego celu klasyczną metodę najmniejszych kwadratów i jej pochodne. Metoda ta jest najstarsza i najłatwiejsza do zastosowania, choć posiada wady (np. niewielką odporność na elementy odstające), które udało się usunąć we wspomnianych pozostałych metodach.

### Agent globalny
Agent globalny odpowiedzialny jest za przydzielanie zasobów chmury w taki sposób, aby
zoptymalizować zużycie energii, a tym samym koszty utrzymania chmury. Optymalizacja odbywa się poprzez
utrzymanie właściwego rozkładu maszyn wirtualnych na maszynach fizycznych oraz propagowanie zadań 
od użytkowników na ich maszyny wirtualne.

Agent globalny redukuje liczbę uruchomionych (a zatem pobierających energię) maszyn fizycznych poprzez 
redukcję aktywnych maszyn wirtualnych i ich migracji. Dokonuje tego poprzez szybkie zwalnianie zasobów
wykorzystywanych przez konkretną maszynę wirtualną, gdy skończy ona wykonywać zadania. Zwolnione zasoby
mogą zostać przydzielone innej maszynie wirtualnej, bez potrzeby alokowania ich na kolejnej maszynie 
fizycznej. Agent globalny pozostaje w ciągłej komunikacji z hipernadzorcami maszyn fizycznych, dzięki 
czemu jest w stanie dostarczać użytkownikom żądane zasoby i zwalniać je, gdy tylko jest to możliwe.

W sytuacji, gdy hipernadzorca zużywa za dużo zasobów powiadamia on o tym globalnego agenta. Globalny agent 
następnie wybiera maszynę wirtualną, która najbardziej wpływa na przeciążenie maszyny fizycznej (np. tę, która 
zużywa najwięcej przeciążonego zasobu) i dokonuje jej migracji do innej maszyny fizycznej.

Jeżeli natomiast zajdzie sytuacja, w której maszyna fizyczna jest obciążona w małym stopniu, a w systemie 
znajdują się inne maszyny, na których mogłyby działać uruchomione na niej maszyny wirtualne, hipernadzorca
również powiadamia globalnego agenta. Globalny agent migruje wtedy wszystkie maszyny wirtualne ze wskazanej
maszyny fizycznej do innych maszyn, redukując w ten sposób liczbę pracujących maszyn o 1. [1]

Redukcja uruchomionych maszyn fizycznych następuje również poprzez przyjęcie konkretnej strategii alokacji
zasobów dla nowych maszyn wirtualnych. Proponujemy przyjęcie strategii podobnej do [2] w której wybieramy zasób, na który nowa 
maszyna ma największe zapotrzebowanie, następnie spośród pracujących maszyn fizycznych wybieramy tę, która posiada go najmniej, ale jest w stanie zapewnić
wystarczająco dużo zasobów nowej maszynie wirtualnej. Jeżeli żadna z pracujących już maszyn fizycznych nie
jest w stanie uruchomić nowej maszyny wirtualnej, dokonujemy aktywacji kolejnej maszyny fizycznej, przy czym
wybierana jest ta o najmniejszym zużyciu energii. 

Aby efektywnie zwalniać zasoby maszyn fizycznych, agent globalny zapewnia również strategię przyporządkowywania 
zadań do maszyn wirtualnych w taki sposób, aby jak najwięcej z nich mogło wykonywać się równolegle. Proponujemy 
metodę opartą o zasoby, analogiczną do alokacji zasobów dla maszyn wirtualnych, która została opisana powyżej.
Wybieramy zasób najistotniejszy dla zadania, następnie maszynę wirtualną, która posiada go najmniej, ale jest 
w stanie zapewnić wykonanie zadania. W przypadku gdy żadna z maszyn użytkownika nie posiada wystarczającej ilości
zasobów,, zadanie jest kolejkowane. Istnieją też inne metody szeregowania zadań, w szczególności takie oparte o czas
wykonania, które dają dobre rezultaty, jednak w środowisku w którym do puli ciągle napływają nowe zadania, istnieje
ryzyko, ze zadania o skrajnie długim lub skrajnie krótkim czasie wykonania nigdy nie zostaną przydzielone maszynie
wirtualnej. Przyjmujemy założenie że czas oczekiwania użytkownika na wykonanie zadania powinien być jak najkrótszy,
dlatego zdecydowaliśmy się wybrać strategię opartą o zasoby. 

### Symulacja środowiska chmurowego

W celu realizacji projektu proponujemy wykonanie pomocnicznych agentów w celu symulacji środowiska chmurowego.
Dostarczamy agenta symulującego zachowanie hipernadzorcy oraz maszyny wirtualnej. Komunikacja pomiędzy agentami
przedstawiona jest na rysuku poniżej:

<p align="center">
  <img src = "./komunikacja.png"/> 
   <figcaption>Komunikacja pomiędzy agentami</figcaption>
</p>

Wymieniane są następujące komunikaty:
 - createVM - żądanie utworzenia maszyny wirtualnej,
 - performTask - żądanie wykonania zadania na jednej z maszyn użytkownika,
 - taskFinished - powiadomienie o ukończeniu zadania,
 - allocateResources - żądanie przydzielenia zasobów uruchomionej lub nowoutworzonej maszynie wirtualnej,
 - freeResources - żądanie zwolnienia zasobów maszyny, która nie wykonuje już zadań,
 - underprovisioning - komunikat o niewystarczająco dużym stopniu alokacji zasobów maszyny fizycznej,
 - overprovisioning - komunikat o nadmiernej alokacji zasobów maszyny fizycznej,
 - attachVM - żądanie przydzielenia zasobów maszynie wirtualnej, wykorzystywane przy tworzeniu nowej maszyny oraz przy migracji,
 - detachVM - komunikat o możliwości usunięcia maszyny z listy danego hipernadzorcy (z maszyny fizycznej), wykorzystywany przy migracji.

### Bibliografia
1. Multi-Agent Based Dynamic Resource Provisioningand Monitoring In Cloud Computing Systems - 
Mahmoud Al-Ayyoub, Mustafa Daraghmeh, Yaser Jararweh and Qutaibah Althebyan
2. Energy Efficient Allocation of Virtual Machines in Cloud Data Centers - Anton Beloglazov and Rajkumar Buyya