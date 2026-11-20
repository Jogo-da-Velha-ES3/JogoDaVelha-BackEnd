UPDATE skills
SET description = 'Congela uma peça do oponente por 4 turnos. A peça permanece no tabuleiro, mas é ignorada no cálculo de vitória.'
WHERE code = 'CONGELAR';

UPDATE skills
SET description = 'Protege uma peça própria contra remoção ou movimentação por 4 turnos.'
WHERE code = 'REFORCO';
