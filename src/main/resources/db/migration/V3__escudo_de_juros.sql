-- Escudo de juros: a conta corrente pode pedir cobertura automática do saldo negativo com a poupança.
ALTER TABLE conta ADD COLUMN escudo_ativo BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE conta ADD COLUMN escudo_desde TIMESTAMP(6) WITH TIME ZONE;
