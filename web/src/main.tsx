import React, { useEffect, useMemo, useState } from 'react';
import ReactDOM from 'react-dom/client';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import './styles.css';
import {
  AppShell,
  Badge,
  Button,
  Card,
  Group,
  Image,
  MantineProvider,
  Modal,
  NumberInput,
  PasswordInput,
  Select,
  Stack,
  Table,
  Tabs,
  Text,
  TextInput,
  Title,
  createTheme
} from '@mantine/core';
import { Notifications, notifications } from '@mantine/notifications';
import { Search, ShoppingCart, Store, UserRound } from 'lucide-react';

type ItemInfo = {
  itemId: string;
  name: string;
  baseItem: string;
  modelKey: string;
  iconUrl?: string | null;
  tags?: string[];
};

type Order = {
  id: number;
  itemId: string;
  upgrades: string;
  quantity: number;
  pricePerUnit: number;
  createdAt: string;
};

type InventoryEntry = {
  source: 'bank' | 'character';
  characterId?: string | null;
  slot: number;
  quantity: number;
  item: ItemInfo;
};

type CharacterSummary = {
  characterId: string;
  classId?: string | null;
  level: number;
  lockedBy?: string | null;
};

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error ?? data.message ?? 'Request failed');
  }
  return data as T;
}

function Login({ onLogin }: { onLogin: () => void }) {
  const [mode, setMode] = useState<'login' | 'setup'>('login');
  const [playerName, setPlayerName] = useState('');
  const [password, setPassword] = useState('');
  const [token, setToken] = useState('');
  const [message, setMessage] = useState('');

  async function submitLogin() {
    await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ playerName, password }) });
    onLogin();
  }

  async function startSetup() {
    const result = await api<{ token: string; message: string }>('/api/auth/setup/start', {
      method: 'POST',
      body: JSON.stringify({ playerName })
    });
    setToken(result.token);
    setMessage(result.message);
  }

  async function finishSetup() {
    await api('/api/auth/setup/finish', { method: 'POST', body: JSON.stringify({ token, password }) });
    onLogin();
  }

  return (
    <div className="loginShell">
      <Card className="loginPanel">
        <Stack gap="md">
          <Group gap="sm">
            <Store size={28} />
            <Title order={2}>Hephaestus Market</Title>
          </Group>
          <Tabs value={mode} onChange={(value) => setMode(value as 'login' | 'setup')}>
            <Tabs.List>
              <Tabs.Tab value="login">Login</Tabs.Tab>
              <Tabs.Tab value="setup">Setup</Tabs.Tab>
            </Tabs.List>
          </Tabs>
          <TextInput label="Minecraft name" value={playerName} onChange={(event) => setPlayerName(event.currentTarget.value)} />
          {mode === 'setup' && !token && <Button onClick={startSetup}>Start setup</Button>}
          {mode === 'setup' && token && (
            <>
              <Text size="sm" c="dimmed">{message}</Text>
              <PasswordInput label="New password" value={password} onChange={(event) => setPassword(event.currentTarget.value)} />
              <Button onClick={finishSetup}>Create account</Button>
            </>
          )}
          {mode === 'login' && (
            <>
              <PasswordInput label="Password" value={password} onChange={(event) => setPassword(event.currentTarget.value)} />
              <Button onClick={submitLogin}>Login</Button>
            </>
          )}
        </Stack>
      </Card>
    </div>
  );
}

function App() {
  const [authenticated, setAuthenticated] = useState(false);
  const [items, setItems] = useState<ItemInfo[]>([]);
  const [orders, setOrders] = useState<{ sellOrders: Order[]; buyOrders: Order[] }>({ sellOrders: [], buyOrders: [] });
  const [ownOrders, setOwnOrders] = useState<{ sellOrders: Order[]; buyOrders: Order[] }>({ sellOrders: [], buyOrders: [] });
  const [collection, setCollection] = useState<{ money: number; items: { id: number; itemId: string; quantity: number }[] }>({ money: 0, items: [] });
  const [inventory, setInventory] = useState<InventoryEntry[]>([]);
  const [characterInventories, setCharacterInventories] = useState<Record<string, InventoryEntry[]>>({});
  const [characters, setCharacters] = useState<CharacterSummary[]>([]);
  const [currentCharacterId, setCurrentCharacterId] = useState<string | null>(null);
  const [selectedSource, setSelectedSource] = useState('bank');
  const [query, setQuery] = useState('');
  const [selectedItem, setSelectedItem] = useState<ItemInfo | null>(null);
  const [buyQuantity, setBuyQuantity] = useState(1);
  const [sellEntry, setSellEntry] = useState<InventoryEntry | null>(null);
  const [sellQuantity, setSellQuantity] = useState(1);
  const [sellPrice, setSellPrice] = useState(1);

  useEffect(() => {
    api('/api/me').then(() => setAuthenticated(true)).catch(() => setAuthenticated(false));
  }, []);

  useEffect(() => {
    if (!authenticated) return;
    refresh();
  }, [authenticated]);

  async function refresh() {
    const [market, mine, inv, collectables] = await Promise.all([
      api<ItemInfo[]>('/api/market/items'),
      api<{ sellOrders: Order[]; buyOrders: Order[] }>('/api/orders'),
      api<{ bank: InventoryEntry[]; currentCharacter: InventoryEntry[]; characterInventories: Record<string, InventoryEntry[]>; characters: CharacterSummary[]; currentCharacterId?: string | null }>('/api/inventory'),
      api<{ money: number; items: { id: number; itemId: string; quantity: number }[] }>('/api/collection')
    ]);
    setItems(market);
    setOwnOrders(mine);
    setInventory([...inv.bank, ...inv.currentCharacter]);
    setCharacterInventories(inv.characterInventories ?? {});
    setCharacters(inv.characters);
    setCurrentCharacterId(inv.currentCharacterId ?? null);
    setCollection(collectables);
  }

  async function openOrders(item: ItemInfo) {
    setSelectedItem(item);
    const data = await api<{ sellOrders: Order[]; buyOrders: Order[] }>(`/api/market/orders?itemId=${encodeURIComponent(item.itemId)}&upgrades=`);
    setOrders(data);
  }

  async function buy() {
    if (!selectedItem) return;
    await api('/api/market/instant-buy', { method: 'POST', body: JSON.stringify({ itemId: selectedItem.itemId, upgrades: '', quantity: buyQuantity }) });
    notifications.show({ color: 'green', message: 'Purchase completed' });
    await openOrders(selectedItem);
    await refresh();
  }

  async function cancel(type: 'sell' | 'buy', orderId: number) {
    await api('/api/orders/cancel', { method: 'POST', body: JSON.stringify({ type, orderId }) });
    notifications.show({ color: 'green', message: 'Order canceled' });
    await refresh();
  }

  async function sell() {
    if (!sellEntry) return;
    await api('/api/market/sell', {
      method: 'POST',
      body: JSON.stringify({
        source: sellEntry.source,
        characterId: sellEntry.characterId,
        slot: sellEntry.slot,
        quantity: sellQuantity,
        pricePerUnit: sellPrice
      })
    });
    notifications.show({ color: 'green', message: 'Sell order created' });
    setSellEntry(null);
    await refresh();
  }

  const filteredItems = useMemo(() => {
    const needle = query.toLowerCase();
    return items.filter((item) => item.itemId.toLowerCase().includes(needle) || item.tags?.some((tag) => tag.toLowerCase().includes(needle)));
  }, [items, query]);

  const sourceOptions = useMemo(() => {
    return [
      { value: 'bank', label: 'Bank' },
      ...characters.map((character) => ({
        value: `character:${character.characterId}`,
        label: `${character.classId ?? 'Character'} · Level ${character.level}`
      }))
    ];
  }, [characters]);

  const selectedInventory = useMemo(() => {
    if (selectedSource === 'bank') {
      return inventory.filter((entry) => entry.source === 'bank');
    }
    const characterId = selectedSource.replace('character:', '');
    return characterInventories[characterId] ?? [];
  }, [characterInventories, inventory, selectedSource]);

  if (!authenticated) {
    return <Login onLogin={() => setAuthenticated(true)} />;
  }

  return (
    <AppShell header={{ height: 64 }} padding="md">
      <AppShell.Header className="topbar">
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm"><Store size={24} /><Title order={3}>Hephaestus Market</Title></Group>
          <Group gap="sm"><UserRound size={18} /><Button variant="subtle" onClick={() => api('/api/auth/logout', { method: 'POST' }).then(() => setAuthenticated(false))}>Logout</Button></Group>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <Tabs defaultValue="market">
          <Tabs.List>
            <Tabs.Tab value="market" leftSection={<Search size={16} />}>Market</Tabs.Tab>
            <Tabs.Tab value="sell" leftSection={<Store size={16} />}>Sell</Tabs.Tab>
            <Tabs.Tab value="orders" leftSection={<ShoppingCart size={16} />}>My Orders</Tabs.Tab>
            <Tabs.Tab value="collection">Collection</Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="market" pt="md">
            <Stack>
              <TextInput placeholder="Search items or tags" value={query} onChange={(event) => setQuery(event.currentTarget.value)} />
              <div className="itemGrid">
                {filteredItems.map((item) => (
                  <Card key={item.itemId} className="itemCard" onClick={() => openOrders(item)}>
                    <Group wrap="nowrap">
                      <Icon item={item} />
                      <Stack gap={4}>
                        <Text fw={700}>{item.itemId}</Text>
                        <Text size="xs" c="dimmed">{item.modelKey}</Text>
                        <Group gap={4}>{item.tags?.slice(0, 3).map((tag) => <Badge key={tag} size="xs" variant="light">{tag}</Badge>)}</Group>
                      </Stack>
                    </Group>
                  </Card>
                ))}
              </div>
            </Stack>
          </Tabs.Panel>

          <Tabs.Panel value="sell" pt="md">
            <Stack gap="md">
            <Select
              label="Inventory source"
              value={selectedSource}
              data={sourceOptions}
              onChange={(value) => setSelectedSource(value ?? 'bank')}
            />
            {selectedInventory.length === 0 && selectedSource !== 'bank' && (
              <Card>
                <Text c="dimmed">This character has no Hephaestus items available for web selling.</Text>
              </Card>
            )}
            <Table highlightOnHover>
              <Table.Thead><Table.Tr><Table.Th>Item</Table.Th><Table.Th>Source</Table.Th><Table.Th>Slot</Table.Th><Table.Th>Qty</Table.Th><Table.Th /></Table.Tr></Table.Thead>
              <Table.Tbody>
                {selectedInventory.map((entry) => (
                  <Table.Tr key={`${entry.source}-${entry.characterId}-${entry.slot}`}>
                    <Table.Td><Group><Icon item={entry.item} /><Text>{entry.item.itemId}</Text></Group></Table.Td>
                    <Table.Td>{entry.source === 'bank' ? 'Bank' : 'Character'}</Table.Td>
                    <Table.Td>{entry.slot}</Table.Td>
                    <Table.Td>{entry.quantity}</Table.Td>
                    <Table.Td><Button size="xs" onClick={() => { setSellEntry(entry); setSellQuantity(1); }}>Sell</Button></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            </Stack>
          </Tabs.Panel>

          <Tabs.Panel value="orders" pt="md">
            <Orders title="Sell Orders" orders={ownOrders.sellOrders} type="sell" onCancel={cancel} />
            <Orders title="Buy Orders" orders={ownOrders.buyOrders} type="buy" onCancel={cancel} />
          </Tabs.Panel>

          <Tabs.Panel value="collection" pt="md">
            <Stack>
              <Card><Text fw={700}>Collectable Herone: {collection.money}</Text></Card>
              <Table>
                <Table.Thead><Table.Tr><Table.Th>Item</Table.Th><Table.Th>Quantity</Table.Th></Table.Tr></Table.Thead>
                <Table.Tbody>
                  {collection.items.map((entry) => (
                    <Table.Tr key={entry.id}><Table.Td>{entry.itemId}</Table.Td><Table.Td>{entry.quantity}</Table.Td></Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Stack>
          </Tabs.Panel>
        </Tabs>
      </AppShell.Main>

      <Modal opened={!!selectedItem} onClose={() => setSelectedItem(null)} title={selectedItem?.itemId} size="lg">
        <Stack>
          <Orders title="Sell Orders" orders={orders.sellOrders} />
          <NumberInput label="Quantity" min={1} value={buyQuantity} onChange={(value) => setBuyQuantity(Number(value) || 1)} />
          <Button onClick={buy}>Instant Buy</Button>
          <Orders title="Buy Orders" orders={orders.buyOrders} />
        </Stack>
      </Modal>

      <Modal opened={!!sellEntry} onClose={() => setSellEntry(null)} title={sellEntry?.item.itemId}>
        <Stack>
          <NumberInput label="Quantity" min={1} max={sellEntry?.quantity ?? 1} value={sellQuantity} onChange={(value) => setSellQuantity(Number(value) || 1)} />
          <NumberInput label="Price per unit" min={1} value={sellPrice} onChange={(value) => setSellPrice(Number(value) || 1)} />
          <Button onClick={sell}>Create Sell Order</Button>
        </Stack>
      </Modal>
    </AppShell>
  );
}

function Orders({ title, orders, type, onCancel }: { title: string; orders: Order[]; type?: 'sell' | 'buy'; onCancel?: (type: 'sell' | 'buy', orderId: number) => void }) {
  return (
    <Stack gap="xs" mb="lg">
      <Title order={4}>{title}</Title>
      <Table>
        <Table.Thead><Table.Tr><Table.Th>Item</Table.Th><Table.Th>Qty</Table.Th><Table.Th>Price</Table.Th><Table.Th>Total</Table.Th><Table.Th /></Table.Tr></Table.Thead>
        <Table.Tbody>
          {orders.map((order) => (
            <Table.Tr key={order.id}>
              <Table.Td>{order.itemId}</Table.Td>
              <Table.Td>{order.quantity}</Table.Td>
              <Table.Td>{order.pricePerUnit}</Table.Td>
              <Table.Td>{order.quantity * order.pricePerUnit}</Table.Td>
              <Table.Td>{type && onCancel && <Button size="xs" variant="light" onClick={() => onCancel(type, order.id)}>Cancel</Button>}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  );
}

function Icon({ item }: { item: ItemInfo }) {
  return <Image className="itemIcon" src={item.iconUrl || undefined} fallbackSrc="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='42' height='42' viewBox='0 0 42 42'%3E%3Crect width='42' height='42' rx='6' fill='%23242b31'/%3E%3Cpath d='M12 12h18v18H12z' fill='%235b6670'/%3E%3C/svg%3E" alt="" />;
}

const theme = createTheme({
  primaryColor: 'teal',
  defaultRadius: 6
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="dark">
      <Notifications />
      <App />
    </MantineProvider>
  </React.StrictMode>
);
