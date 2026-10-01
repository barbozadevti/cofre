-- Dois tipos de conta na mesma tabela (herança JPA em tabela única).
-- As contas que já existem são correntes; a poupança não usa a coluna limite (fica zero).
ALTER TABLE conta ADD COLUMN tipo VARCHAR(10) DEFAULT 'CORRENTE' NOT NULL;
ALTER TABLE conta ALTER COLUMN limite SET DEFAULT 0;
