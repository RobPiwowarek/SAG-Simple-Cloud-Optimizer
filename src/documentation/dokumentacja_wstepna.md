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
